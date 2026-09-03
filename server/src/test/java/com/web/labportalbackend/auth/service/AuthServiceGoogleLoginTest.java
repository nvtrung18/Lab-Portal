package com.web.labportalbackend.auth.service;

import com.web.labportalbackend.admin.audit.service.AuditLogService;
import com.web.labportalbackend.auth.dto.AuthResponse;
import com.web.labportalbackend.auth.dto.GoogleAuthRequest;
import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.RoleRepository;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.auth.security.GoogleIdentity;
import com.web.labportalbackend.auth.security.GoogleIdentityVerifier;
import com.web.labportalbackend.auth.security.JwtProvider;
import com.web.labportalbackend.auth.service.impl.AuthServiceImpl;
import com.web.labportalbackend.common.email.EmailService;
import com.web.labportalbackend.common.enums.UserStatus;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceGoogleLoginTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private LaboratoryRepository laboratoryRepository;
    @Mock private EmailService emailService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtProvider jwtProvider;
    @Mock private RedisOtpService redisOtpService;
    @Mock private AuditLogService auditLogService;
    @Mock private GoogleIdentityVerifier googleIdentityVerifier;

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(authenticationManager, userRepository, roleRepository,
                laboratoryRepository, emailService, passwordEncoder, jwtProvider, googleIdentityVerifier,
                redisOtpService, auditLogService);
        ReflectionTestUtils.setField(service, "accessTokenExpiration", 86_400_000L);
    }

    @Test
    void newGoogleIdentityCreatesActiveStudentAndReturnsInternalTokens() {
        GoogleIdentity identity = new GoogleIdentity("google-subject", "student@gmail.com", "Nguyen Van A", true);
        Role student = new Role("STUDENT", "Student");
        when(googleIdentityVerifier.verify("credential")).thenReturn(identity);
        when(userRepository.findByGoogleSubject("google-subject")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("student@gmail.com")).thenReturn(Optional.empty());
        when(roleRepository.findByName("STUDENT")).thenReturn(Optional.of(student));
        when(userRepository.existsByUsername("student")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("encoded-random-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(42L);
            return user;
        });
        when(jwtProvider.generateAccessToken("student", "ROLE_STUDENT")).thenReturn("access-token");
        when(jwtProvider.generateRefreshToken("student")).thenReturn("refresh-token");

        AuthResponse response = service.loginWithGoogle(request());

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRoles()).containsExactly("STUDENT");
        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().getGoogleSubject()).isEqualTo("google-subject");
        assertThat(savedUser.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(savedUser.getValue().getPassword()).isEqualTo("encoded-random-password");
    }

    @Test
    void authoritativeEmailLinksAnExistingActiveAccount() {
        User existing = activeUser("existing", "student@gmail.com");
        existing.addRole(new Role("STUDENT", "Student"));
        GoogleIdentity identity = new GoogleIdentity("google-subject", existing.getEmail(), "Student", true);
        when(googleIdentityVerifier.verify("credential")).thenReturn(identity);
        when(userRepository.findByGoogleSubject("google-subject")).thenReturn(Optional.empty());
        when(userRepository.findByEmail(existing.getEmail())).thenReturn(Optional.of(existing));
        when(userRepository.save(existing)).thenReturn(existing);
        when(jwtProvider.generateAccessToken("existing", "ROLE_STUDENT")).thenReturn("access-token");
        when(jwtProvider.generateRefreshToken("existing")).thenReturn("refresh-token");

        service.loginWithGoogle(request());

        assertThat(existing.getGoogleSubject()).isEqualTo("google-subject");
        verify(userRepository).save(existing);
    }

    @Test
    void thirdPartyEmailCollisionIsNotAutomaticallyLinked() {
        User existing = activeUser("existing", "student@example.com");
        GoogleIdentity identity = new GoogleIdentity("google-subject", existing.getEmail(), "Student", false);
        when(googleIdentityVerifier.verify("credential")).thenReturn(identity);
        when(userRepository.findByGoogleSubject("google-subject")).thenReturn(Optional.empty());
        when(userRepository.findByEmail(existing.getEmail())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.loginWithGoogle(request()))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("đăng nhập bằng mật khẩu");
    }

    private GoogleAuthRequest request() {
        GoogleAuthRequest request = new GoogleAuthRequest();
        request.setCredential("credential");
        return request;
    }

    private User activeUser(String username, String email) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword("encoded-password");
        user.setStatus(UserStatus.ACTIVE);
        user.setActive(true);
        user.setDeleted(false);
        return user;
    }
}
