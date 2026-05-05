package com.web.labportalbackend.auth.service;

import com.web.labportalbackend.auth.dto.AuthResponse;
import com.web.labportalbackend.auth.dto.LoginRequest;
import com.web.labportalbackend.auth.dto.RegisterRequest;
import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.RoleRepository;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.auth.security.JwtProvider;
import com.web.labportalbackend.common.enums.UserStatus;
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

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Authentication service handling login, registration, and token refresh.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    /**
     * Authenticate user credentials and return JWT tokens.
     */
    public AuthResponse login(LoginRequest request) {
        // Authenticate via Spring Security AuthenticationManager
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsernameOrEmail(),
                        request.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        // Load the full user entity to get roles and ID
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        return buildAuthResponse(user);
    }

    /**
     * Register a new user account with STUDENT role by default.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Validate uniqueness
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email is already registered: " + request.getEmail());
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username is already taken: " + request.getUsername());
        }

        // Find default role
        Role studentRole = roleRepository.findByName("STUDENT")
                .orElseThrow(() -> new IllegalStateException("Default role STUDENT not found in database"));

        // Create user
        User user = new User();
        user.setEmail(request.getEmail());
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());
        user.setStatus(UserStatus.ACTIVE);
        user.addRole(studentRole);

        User savedUser = userRepository.save(user);
        log.info("New user registered: {} with role STUDENT", savedUser.getUsername());

        return buildAuthResponse(savedUser);
    }

    /**
     * Refresh an expired access token using a valid refresh token.
     */
    public AuthResponse refreshToken(String refreshToken) {
        if (!jwtProvider.validateToken(refreshToken)) {
            throw new BadCredentialsException("Invalid or expired refresh token");
        }

        String username = jwtProvider.getUsernameFromToken(refreshToken);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        return buildAuthResponse(user);
    }

    // ---- internal ----

    private AuthResponse buildAuthResponse(User user) {
        Set<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        String rolesString = String.join(",", roleNames.stream()
                .map(r -> "ROLE_" + r)
                .toList());

        String accessToken = jwtProvider.generateAccessToken(user.getUsername(), rolesString);
        String refreshToken = jwtProvider.generateRefreshToken(user.getUsername());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(accessTokenExpiration)
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .roles(roleNames)
                .build();
    }
}
