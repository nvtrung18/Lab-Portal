package com.web.labportalbackend.auth.service.impl;

import com.web.labportalbackend.auth.dto.*;
import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.mapper.AuthMapper;
import com.web.labportalbackend.auth.repository.RoleRepository;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.auth.security.JwtProvider;
import com.web.labportalbackend.auth.security.GoogleIdentity;
import com.web.labportalbackend.auth.security.GoogleIdentityVerifier;
import com.web.labportalbackend.auth.service.AuthService;
import com.web.labportalbackend.auth.service.RedisOtpService;
import com.web.labportalbackend.admin.audit.enums.AuditAction;
import com.web.labportalbackend.admin.audit.enums.AuditModule;
import com.web.labportalbackend.admin.audit.service.AuditLogService;
import com.web.labportalbackend.common.email.EmailService;
import com.web.labportalbackend.common.enums.UserStatus;
import com.web.labportalbackend.common.enums.LabStatus;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.lab.entity.Laboratory;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final Duration OTP_TTL = Duration.ofMinutes(10);
    private static final Duration TOKEN_TTL = Duration.ofMinutes(15);
    private static final String REGISTER_OTP_PREFIX = "register:otp:";
    private static final String REGISTER_VERIFIED_PREFIX = "register:verified:";
    private static final String PASSWORD_RESET_OTP_PREFIX = "password-reset:otp:";
    private static final String PASSWORD_RESET_VERIFIED_PREFIX = "password-reset:verified:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final GoogleIdentityVerifier googleIdentityVerifier;
    private final RedisOtpService redisOtpService;
    private final AuditLogService auditLogService;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Override
    public AuthResponse login(LoginRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        SecurityContextHolder.getContext().setAuthentication(auth);
        User user = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new BadCredentialsException("User not found"));
        return buildAuthResponse(user);
    }

    @Override
    @Transactional
    public AuthResponse loginWithGoogle(GoogleAuthRequest request) {
        GoogleIdentity identity = googleIdentityVerifier.verify(request.getCredential());
        User user = userRepository.findByGoogleSubject(identity.subject())
                .orElseGet(() -> linkOrCreateGoogleUser(identity));
        ensureLoginAllowed(user);
        return buildAuthResponse(user);
    }

    @Override
    public AuthEmailResponse sendRegistrationCode(RegisterSendCodeRequest request) {
        String email = normalizeEmail(request.getEmail());
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email đã được sử dụng.");
        }

        String code = createOtp();
        redisOtpService.saveOtp(registerOtpKey(email), code, OTP_TTL);
        emailService.sendRegisterOtp(email, code);

        return AuthEmailResponse.builder()
                .email(email)
                .message("Mã xác nhận đã được gửi tới email của bạn.")
                .build();
    }

    @Override
    public RegisterVerifyCodeResponse verifyRegistrationCode(VerifyRegisterRequest request) {
        String email = normalizeEmail(request.getEmail());
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email đã được sử dụng.");
        }

        verifyOtp(registerOtpKey(email), request.getCode());
        String verificationToken = UUID.randomUUID().toString();
        redisOtpService.saveVerifiedToken(registerVerifiedKey(verificationToken), email, TOKEN_TTL);

        return RegisterVerifyCodeResponse.builder()
                .email(email)
                .verificationToken(verificationToken)
                .message("Email đã được xác thực.")
                .build();
    }

    @Override
    @Transactional
    public AuthEmailResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());
        String tokenKey = registerVerifiedKey(request.getVerificationToken());
        String verifiedEmail = redisOtpService.getVerifiedEmail(tokenKey);

        if (verifiedEmail == null) {
            throw new IllegalArgumentException("Phiên xác thực email đã hết hạn. Vui lòng gửi lại mã xác nhận.");
        }
        if (!verifiedEmail.equals(email)) {
            throw new IllegalArgumentException("Email không khớp với phiên xác thực.");
        }
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email đã được sử dụng.");
        }

        Role studentRole = roleRepository.findByName("STUDENT")
                .orElseThrow(() -> new IllegalStateException("Default role STUDENT not found"));

        User user = new User();
        user.setEmail(email);
        user.setUsername(resolveUsername(request.getUsername(), email));
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());
        user.setStatus(UserStatus.ACTIVE);
        user.setActive(true);
        user.addRole(studentRole);

        User saved = userRepository.save(user);
        redisOtpService.delete(tokenKey);
        log.info("Registered verified STUDENT account: {}", saved.getEmail());

        return AuthEmailResponse.builder()
                .email(saved.getEmail())
                .message("Đăng ký tài khoản thành công.")
                .build();
    }

    @Override
    public AuthEmailResponse sendPasswordResetCode(ForgotPasswordRequest request) {
        String email = normalizeEmail(request.getEmail());
        userRepository.findByEmail(email).ifPresent(user -> {
            String code = createOtp();
            redisOtpService.saveOtp(passwordResetOtpKey(email), code, OTP_TTL);
            emailService.sendPasswordResetOtp(email, code);
        });

        return AuthEmailResponse.builder()
                .email(email)
                .message("Nếu email tồn tại trong hệ thống, mã xác nhận sẽ được gửi tới email.")
                .build();
    }

    @Override
    public PasswordResetVerifyResponse verifyPasswordResetCode(VerifyRegisterRequest request) {
        String email = normalizeEmail(request.getEmail());
        verifyOtp(passwordResetOtpKey(email), request.getCode());

        String resetToken = UUID.randomUUID().toString();
        redisOtpService.saveVerifiedToken(passwordResetVerifiedKey(resetToken), email, TOKEN_TTL);

        return PasswordResetVerifyResponse.builder()
                .resetToken(resetToken)
                .message("Mã xác nhận hợp lệ.")
                .build();
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String email = normalizeEmail(request.getEmail());
        String tokenKey = passwordResetVerifiedKey(request.getResetToken());
        String verifiedEmail = redisOtpService.getVerifiedEmail(tokenKey);

        if (verifiedEmail == null) {
            throw new IllegalArgumentException("Phiên đặt lại mật khẩu đã hết hạn. Vui lòng gửi lại mã xác nhận.");
        }
        if (!verifiedEmail.equals(email)) {
            throw new IllegalArgumentException("Email không khớp với phiên đặt lại mật khẩu.");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        redisOtpService.delete(tokenKey);
    }

    @Override
    public AuthResponse refreshToken(String refreshToken) {
        if (!jwtProvider.validateToken(refreshToken)) {
            throw new BadCredentialsException("Invalid or expired refresh token");
        }
        String username = jwtProvider.getUsernameFromToken(refreshToken);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("User not found"));
        return buildAuthResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleResponse> getAllRoles() {
        return roleRepository.findAll().stream()
                .filter(role -> !"ADMIN".equalsIgnoreCase(role.getName()))
                .map(AuthMapper::toRoleResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .filter(user -> !user.hasRole("ADMIN"))
                .filter(user -> user.getStatus() != UserStatus.PENDING_VERIFICATION)
                .map(this::mapUserToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + id));
        if (user.hasRole("ADMIN")) {
            throw new EntityNotFoundException("User not found with ID: " + id);
        }
        return mapUserToResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateUserRoles(Long id, UpdateUserRolesRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + id));
        if (user.hasRole("ADMIN")) {
            throw new IllegalArgumentException("Không thể quản lý tài khoản ADMIN.");
        }
        Set<String> roles = request.getRoles();
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("Roles are required");
        }
        Set<String> normalizedRoles = roles.stream()
                .map(role -> role.replace("ROLE_", "").toUpperCase())
                .collect(Collectors.toSet());
        if (normalizedRoles.contains("ADMIN")) {
            throw new IllegalArgumentException("Không thể phân quyền ADMIN tại màn quản lý Users.");
        }

        boolean willBeManager = normalizedRoles.contains("LAB_MANAGER");
        boolean wasManager = user.hasRole("LAB_MANAGER");

        if (willBeManager) {
            Long managedLabId = request.getManagedLabId();
            if (managedLabId == null) {
                throw new IllegalArgumentException("Bắt buộc chọn PTN khi đổi vai trò sang LAB_MANAGER.");
            }
            Laboratory lab = laboratoryRepository.findById(managedLabId)
                    .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy PTN với ID: " + managedLabId));

            // 1. Nếu user đang quản lý PTN khác, gỡ gán khỏi PTN cũ đó
            laboratoryRepository.findFirstByManagerIdAndDeletedFalse(user.getId())
                    .filter(existingLab -> !existingLab.getId().equals(managedLabId))
                    .ifPresent(existingLab -> {
                        existingLab.setManager(null);
                        laboratoryRepository.save(existingLab);
                        auditLogService.logCurrentUser(
                                AuditAction.ASSIGN_MANAGER,
                                AuditModule.LAB,
                                "LAB",
                                existingLab.getId(),
                                "Admin đã gỡ gán manager " + displayName(user) + " khỏi PTN " + existingLab.getLabName() + " do phân công PTN mới."
                        );
                    });

            // 2. Nếu PTN mới đang có manager khác, gỡ gán manager cũ đó
            User oldManager = lab.getManager();
            if (oldManager != null && !oldManager.getId().equals(user.getId())) {
                lab.setManager(null);
                laboratoryRepository.save(lab);
                auditLogService.logCurrentUser(
                        AuditAction.ASSIGN_MANAGER,
                        AuditModule.LAB,
                        "LAB",
                        lab.getId(),
                        "Admin đã gỡ gán manager " + displayName(oldManager) + " khỏi PTN " + lab.getLabName() + " (do thay thế bởi " + displayName(user) + ")."
                );
            }

            // 3. Gán manager mới cho PTN
            lab.setManager(user);
            laboratoryRepository.save(lab);
            auditLogService.logCurrentUser(
                    AuditAction.ASSIGN_MANAGER,
                    AuditModule.LAB,
                    "LAB",
                    lab.getId(),
                    "Admin đã gán " + displayName(user) + " quản lý PTN " + lab.getLabName() + "."
            );
        } else if (wasManager) {
            // Đổi từ LAB_MANAGER về STUDENT hoặc vai trò khác -> gỡ gán khỏi PTN cũ
            laboratoryRepository.findFirstByManagerIdAndDeletedFalse(user.getId())
                    .ifPresent(existingLab -> {
                        existingLab.setManager(null);
                        laboratoryRepository.save(existingLab);
                        auditLogService.logCurrentUser(
                                AuditAction.ASSIGN_MANAGER,
                                AuditModule.LAB,
                                "LAB",
                                existingLab.getId(),
                                "Admin đã gỡ gán manager " + displayName(user) + " khỏi PTN " + existingLab.getLabName() + " (do đổi vai trò về " + String.join(", ", normalizedRoles) + ")."
                        );
                    });
        }

        Set<Role> nextRoles = new HashSet<>();
        for (String roleName : normalizedRoles) {
            Role role = roleRepository.findByName(roleName)
                    .orElseThrow(() -> new EntityNotFoundException("Role not found: " + roleName));
            nextRoles.add(role);
        }
        user.setRoles(nextRoles);
        User saved = userRepository.save(user);

        auditLogService.logCurrentUser(
                AuditAction.CHANGE_USER_ROLE,
                AuditModule.USER,
                "USER",
                saved.getId(),
                "Admin đã đổi vai trò của " + displayName(saved) + " thành " + String.join(", ", normalizedRoles) + "."
        );
        return mapUserToResponse(saved);
    }

    @Override
    @Transactional
    public UpdateUserRoleResponse patchUserRole(Long id, UpdateUserRoleRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + id));

        // 2. Không cho đổi role của ADMIN chính.
        if (user.hasRole("ADMIN")) {
            throw new IllegalArgumentException("Không thể thay đổi vai trò của tài khoản ADMIN.");
        }

        String newRoleName = request.getRole().replace("ROLE_", "").toUpperCase();

        // 3. Không cho tạo thêm ADMIN qua API này.
        if (newRoleName.equals("ADMIN")) {
            throw new IllegalArgumentException("Không thể phân quyền ADMIN qua API này.");
        }

        // Validate roles
        Role targetRole = roleRepository.findByName(newRoleName)
                .orElseThrow(() -> new EntityNotFoundException("Role not found: " + newRoleName));

        boolean willBeManager = newRoleName.equals("LAB_MANAGER");
        boolean wasManager = user.hasRole("LAB_MANAGER");

        Long labId = request.getLabId();
        Laboratory assignedLab = null;

        if (willBeManager) {
            // 4. Nếu role = LAB_MANAGER
            // labId bắt buộc
            if (labId == null) {
                throw new IllegalArgumentException("Bắt buộc chọn PTN khi đổi vai trò sang LAB_MANAGER.");
            }

            // labId phải tồn tại
            assignedLab = laboratoryRepository.findById(labId)
                    .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy PTN với ID: " + labId));

            // lab phải ACTIVE hoặc AVAILABLE (ở hệ thống này status là enum LabStatus.AVAILABLE, CLOSED, MAINTENANCE, INACTIVE)
            // status không được là INACTIVE hoặc CLOSED
            if (assignedLab.getStatus() == LabStatus.INACTIVE || assignedLab.getStatus() == LabStatus.CLOSED) {
                throw new IllegalArgumentException("Chỉ có thể gán người quản lý cho PTN đang hoạt động.");
            }

            // User không được bị BANNED/DISABLED (UserStatus: ACTIVE, PENDING_VERIFICATION, INACTIVE, SUSPENDED)
            if (user.getStatus() == UserStatus.SUSPENDED || user.getStatus() == UserStatus.INACTIVE) {
                throw new IllegalArgumentException("Không thể gán quyền quản lý cho tài khoản đang bị khóa hoặc vô hiệu hóa.");
            }

            // Nếu user đang quản lý PTN khác thì báo lỗi
            laboratoryRepository.findFirstByManagerIdAndDeletedFalse(user.getId())
                    .filter(existingLab -> !existingLab.getId().equals(labId))
                    .ifPresent(existingLab -> {
                        throw new IllegalArgumentException("Người dùng đã là quản lý của phòng thí nghiệm: " + existingLab.getLabName());
                    });

            // Nếu lab đã có manager khác thì báo lỗi (Option A)
            User currentLabManager = assignedLab.getManager();
            if (currentLabManager != null && !currentLabManager.getId().equals(user.getId())) {
                throw new IllegalArgumentException("PTN này đã có quản lý.");
            }

            // Gán lab mới
            assignedLab.setManager(user);
            laboratoryRepository.save(assignedLab);

            auditLogService.logCurrentUser(
                    AuditAction.ASSIGN_MANAGER,
                    AuditModule.LAB,
                    "LAB",
                    assignedLab.getId(),
                    "Admin đã gán " + displayName(user) + " quản lý " + assignedLab.getLabName() + "."
            );
        } else if (wasManager) {
            // 5. Nếu role chuyển từ LAB_MANAGER về STUDENT:
            // Bỏ gán lab cũ nếu user đang quản lý lab đó
            laboratoryRepository.findFirstByManagerIdAndDeletedFalse(user.getId())
                    .ifPresent(existingLab -> {
                        existingLab.setManager(null);
                        laboratoryRepository.save(existingLab);
                        auditLogService.logCurrentUser(
                                AuditAction.ASSIGN_MANAGER,
                                AuditModule.LAB,
                                "LAB",
                                existingLab.getId(),
                                "Admin đã gỡ gán manager " + displayName(user) + " khỏi PTN " + existingLab.getLabName() + " (do đổi vai trò về " + newRoleName + ")."
                        );
                    });
        }

        // Update user.role
        Set<Role> nextRoles = new HashSet<>();
        nextRoles.add(targetRole);
        user.setRoles(nextRoles);
        User savedUser = userRepository.save(user);

        auditLogService.logCurrentUser(
                AuditAction.CHANGE_USER_ROLE,
                AuditModule.USER,
                "USER",
                savedUser.getId(),
                "Admin đã đổi vai trò của " + displayName(savedUser) + " thành " + newRoleName + "."
        );

        // Fetch lab information for the response
        Long finalManagedLabId = null;
        String finalManagedLabName = null;

        if (willBeManager && assignedLab != null) {
            finalManagedLabId = assignedLab.getId();
            finalManagedLabName = assignedLab.getLabName();
        } else {
            Laboratory currentLab = laboratoryRepository.findFirstByManagerIdAndDeletedFalse(savedUser.getId()).orElse(null);
            if (currentLab != null) {
                finalManagedLabId = currentLab.getId();
                finalManagedLabName = currentLab.getLabName();
            }
        }

        String responseMessage = newRoleName.equals("STUDENT")
                ? "Đã chuyển người dùng về vai trò sinh viên."
                : "Đã cập nhật vai trò người dùng.";

        return UpdateUserRoleResponse.builder()
                .message(responseMessage)
                .user(UpdateUserRoleResponse.UserRoleInfo.builder()
                        .id(savedUser.getId())
                        .fullName(savedUser.getFullName())
                        .email(savedUser.getEmail())
                        .role(newRoleName)
                        .managedLabId(finalManagedLabId)
                        .managedLabName(finalManagedLabName)
                        .build())
                .build();
    }

    @Override
    @Transactional
    public UserResponse banUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + id));
        if (user.hasRole("ADMIN")) {
            throw new IllegalArgumentException("Không thể khóa tài khoản ADMIN.");
        }
        if (user.hasRole("LAB_MANAGER") && laboratoryRepository.findFirstByManagerIdAndDeletedFalse(id).isPresent()) {
            throw new IllegalArgumentException("Vui lòng gỡ manager khỏi lab trước khi khóa tài khoản.");
        }
        user.setStatus(UserStatus.SUSPENDED);
        user.setActive(false);
        User saved = userRepository.save(user);
        auditLogService.logCurrentUser(
                AuditAction.BAN_USER,
                AuditModule.USER,
                "USER",
                saved.getId(),
                "Admin đã khóa tài khoản " + displayName(saved) + "."
        );
        return mapUserToResponse(saved);
    }

    @Override
    @Transactional
    public UserResponse unbanUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + id));
        if (user.hasRole("ADMIN")) {
            throw new IllegalArgumentException("Không thể quản lý tài khoản ADMIN.");
        }
        user.setStatus(UserStatus.ACTIVE);
        user.setActive(true);
        User saved = userRepository.save(user);
        auditLogService.logCurrentUser(
                AuditAction.UNBAN_USER,
                AuditModule.USER,
                "USER",
                saved.getId(),
                "Admin đã mở khóa tài khoản " + displayName(saved) + "."
        );
        return mapUserToResponse(saved);
    }

    private String displayName(User user) {
        return user.getFullName() == null || user.getFullName().isBlank()
                ? user.getUsername()
                : user.getFullName();
    }

    private UserResponse mapUserToResponse(User user) {
        if (user.hasRole("LAB_MANAGER")) {
            Laboratory lab = laboratoryRepository.findFirstByManagerIdAndDeletedFalse(user.getId()).orElse(null);
            if (lab != null) {
                return AuthMapper.toUserResponse(user, lab.getId(), lab.getLabName());
            }
        }
        return AuthMapper.toUserResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        Set<String> roleNames = user.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
        String rolesString = String.join(",", roleNames.stream().map(r -> "ROLE_" + r).toList());
        return AuthResponse.builder()
                .accessToken(jwtProvider.generateAccessToken(user.getUsername(), rolesString))
                .refreshToken(jwtProvider.generateRefreshToken(user.getUsername()))
                .expiresIn(accessTokenExpiration)
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .roles(roleNames)
                .build();
    }

    private User linkOrCreateGoogleUser(GoogleIdentity identity) {
        return userRepository.findByEmail(identity.email())
                .map(existing -> linkExistingGoogleUser(existing, identity))
                .orElseGet(() -> createGoogleUser(identity));
    }

    private User linkExistingGoogleUser(User user, GoogleIdentity identity) {
        if (!identity.authoritativeEmail()) {
            throw new BadCredentialsException(
                    "Email đã có tài khoản. Vui lòng đăng nhập bằng mật khẩu trước khi liên kết Google.");
        }
        ensureLoginAllowed(user);
        user.setGoogleSubject(identity.subject());
        return userRepository.save(user);
    }

    private User createGoogleUser(GoogleIdentity identity) {
        Role studentRole = roleRepository.findByName("STUDENT")
                .orElseThrow(() -> new IllegalStateException("Default role STUDENT not found"));
        User user = new User();
        user.setEmail(identity.email());
        user.setUsername(resolveUsername(null, identity.email()));
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString() + UUID.randomUUID()));
        user.setGoogleSubject(identity.subject());
        user.setFullName(identity.fullName());
        user.setStatus(UserStatus.ACTIVE);
        user.setActive(true);
        user.addRole(studentRole);
        return userRepository.save(user);
    }

    private void ensureLoginAllowed(User user) {
        if (!Boolean.TRUE.equals(user.getActive())
                || Boolean.TRUE.equals(user.getDeleted())
                || user.getStatus() != UserStatus.ACTIVE) {
            throw new BadCredentialsException("Account is not active");
        }
    }

    private void verifyOtp(String key, String code) {
        String codeHash = redisOtpService.getOtp(key);
        if (codeHash == null) {
            throw new IllegalArgumentException("Mã xác nhận đã hết hạn.");
        }
        if (!passwordEncoder.matches(code, codeHash)) {
            throw new IllegalArgumentException("Mã xác nhận không chính xác.");
        }
        redisOtpService.delete(key);
    }

    private String createOtp() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private String resolveUsername(String requestedUsername, String email) {
        if (requestedUsername != null && !requestedUsername.isBlank()) {
            String username = requestedUsername.trim().toLowerCase();
            if (userRepository.existsByUsername(username)) {
                throw new IllegalArgumentException("Username already taken: " + username);
            }
            return username;
        }

        String localPart = email.substring(0, email.indexOf('@')).replaceAll("[^a-zA-Z0-9._-]", "");
        String base = localPart.isBlank() ? "student" : localPart.toLowerCase();
        base = base.length() > 40 ? base.substring(0, 40) : base;
        String username = base;
        int suffix = 1;
        while (userRepository.existsByUsername(username)) {
            username = base + suffix;
            suffix++;
        }
        return username;
    }

    private String registerOtpKey(String email) {
        return REGISTER_OTP_PREFIX + email;
    }

    private String registerVerifiedKey(String token) {
        return REGISTER_VERIFIED_PREFIX + token;
    }

    private String passwordResetOtpKey(String email) {
        return PASSWORD_RESET_OTP_PREFIX + email;
    }

    private String passwordResetVerifiedKey(String token) {
        return PASSWORD_RESET_VERIFIED_PREFIX + token;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssignableManagerResponse> getAssignableManagers() {
        return userRepository.findAll().stream()
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .filter(user -> user.hasRole("LAB_MANAGER"))
                .filter(user -> laboratoryRepository.findFirstByManagerIdAndDeletedFalse(user.getId()).isEmpty())
                .map(user -> AssignableManagerResponse.builder()
                        .id(user.getId())
                        .fullName(user.getFullName())
                        .email(user.getEmail())
                        .role("LAB_MANAGER")
                        .managedLabId(null)
                        .build())
                .toList();
    }
}
