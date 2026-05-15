package com.web.labportalbackend.auth.service.impl;

import com.web.labportalbackend.auth.dto.*;
import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.mapper.AuthMapper;
import com.web.labportalbackend.auth.repository.RoleRepository;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.auth.security.JwtProvider;
import com.web.labportalbackend.auth.service.AuthService;
import com.web.labportalbackend.common.enums.UserStatus;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
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
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

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

    @Override @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail()))
            throw new IllegalArgumentException("Email already registered: " + request.getEmail());
        if (userRepository.existsByUsername(request.getUsername()))
            throw new IllegalArgumentException("Username already taken: " + request.getUsername());
        Role studentRole = roleRepository.findByName("STUDENT")
                .orElseThrow(() -> new IllegalStateException("Default role STUDENT not found"));
        User user = new User();
        user.setEmail(request.getEmail()); user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName()); user.setPhone(request.getPhone());
        user.setStatus(UserStatus.ACTIVE); user.addRole(studentRole);
        User saved = userRepository.save(user);
        log.info("User registered: {} with role STUDENT", saved.getUsername());
        return buildAuthResponse(saved);
    }

    @Override
    public AuthResponse refreshToken(String refreshToken) {
        if (!jwtProvider.validateToken(refreshToken))
            throw new BadCredentialsException("Invalid or expired refresh token");
        String username = jwtProvider.getUsernameFromToken(refreshToken);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("User not found"));
        return buildAuthResponse(user);
    }

    @Override @Transactional(readOnly = true)
    public List<RoleResponse> getAllRoles() {
        return roleRepository.findAll().stream().map(AuthMapper::toRoleResponse).toList();
    }

    @Override @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream().map(AuthMapper::toUserResponse).toList();
    }

    @Override @Transactional(readOnly = true)
    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + id));
        return AuthMapper.toUserResponse(user);
    }

    @Override @Transactional
    public UserResponse updateUserRoles(Long id, Set<String> roles) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + id));
        if (user.hasRole("ADMIN")) {
            throw new IllegalArgumentException("ADMIN user cannot be managed");
        }
        Set<String> normalizedRoles = roles.stream()
                .map(role -> role.replace("ROLE_", "").toUpperCase())
                .collect(Collectors.toSet());
        if (normalizedRoles.contains("ADMIN")) {
            throw new IllegalArgumentException("Cannot assign ADMIN role from admin user management");
        }
        if (user.hasRole("LAB_MANAGER") && !normalizedRoles.contains("LAB_MANAGER")
                && laboratoryRepository.findFirstByManagerIdAndDeletedFalse(id).isPresent()) {
            throw new IllegalArgumentException("Please unassign this manager from lab before changing role");
        }
        Set<Role> nextRoles = new HashSet<>();
        for (String roleName : normalizedRoles) {
            Role role = roleRepository.findByName(roleName)
                    .orElseThrow(() -> new EntityNotFoundException("Role not found: " + roleName));
            nextRoles.add(role);
        }
        user.setRoles(nextRoles);
        return AuthMapper.toUserResponse(userRepository.save(user));
    }

    @Override @Transactional
    public UserResponse banUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + id));
        if (user.hasRole("ADMIN")) {
            throw new IllegalArgumentException("ADMIN user cannot be banned");
        }
        if (user.hasRole("LAB_MANAGER") && laboratoryRepository.findFirstByManagerIdAndDeletedFalse(id).isPresent()) {
            throw new IllegalArgumentException("Please unassign this manager from lab before banning");
        }
        user.setStatus(UserStatus.SUSPENDED);
        user.setActive(false);
        return AuthMapper.toUserResponse(userRepository.save(user));
    }

    @Override @Transactional
    public UserResponse unbanUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + id));
        if (user.hasRole("ADMIN")) {
            throw new IllegalArgumentException("ADMIN user cannot be managed");
        }
        user.setStatus(UserStatus.ACTIVE);
        user.setActive(true);
        return AuthMapper.toUserResponse(userRepository.save(user));
    }

    private AuthResponse buildAuthResponse(User user) {
        Set<String> roleNames = user.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
        String rolesString = String.join(",", roleNames.stream().map(r -> "ROLE_" + r).toList());
        return AuthResponse.builder()
                .accessToken(jwtProvider.generateAccessToken(user.getUsername(), rolesString))
                .refreshToken(jwtProvider.generateRefreshToken(user.getUsername()))
                .expiresIn(accessTokenExpiration).userId(user.getId())
                .username(user.getUsername()).email(user.getEmail()).roles(roleNames).build();
    }
}
