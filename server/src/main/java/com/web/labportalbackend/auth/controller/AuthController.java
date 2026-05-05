package com.web.labportalbackend.auth.controller;

import com.web.labportalbackend.auth.dto.*;
import com.web.labportalbackend.auth.mapper.AuthMapper;
import com.web.labportalbackend.auth.repository.RoleRepository;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.auth.service.AuthService;
import com.web.labportalbackend.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Authentication & user management endpoints.
 * <p>
 * Public endpoints: login, register, refresh-token, health.
 * Protected endpoints (require JWT): users, roles listing.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication, user and role management")
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    // ===================== Public Auth Endpoints =====================

    @PostMapping("/login")
    @Operation(summary = "User login", description = "Authenticate with email/username and password, returns JWT tokens")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        try {
            AuthResponse authResponse = authService.login(request);
            return ResponseEntity.ok(ApiResponse.success("Login successful", authResponse));
        } catch (BadCredentialsException e) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Invalid credentials: wrong username/email or password"));
        }
    }

    @PostMapping("/register")
    @Operation(summary = "User registration", description = "Register a new user account with STUDENT role")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse authResponse = authService.register(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Registration successful", authResponse));
    }

    @PostMapping("/refresh-token")
    @Operation(summary = "Refresh token", description = "Get new access token using a valid refresh token")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        try {
            AuthResponse authResponse = authService.refreshToken(request.getRefreshToken());
            return ResponseEntity.ok(ApiResponse.success("Token refreshed", authResponse));
        } catch (BadCredentialsException e) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Invalid or expired refresh token"));
        }
    }

    @GetMapping("/health")
    @Operation(summary = "Auth service health check")
    public ResponseEntity<ApiResponse<String>> health() {
        return ResponseEntity.ok(ApiResponse.success("Auth service is healthy", "UP"));
    }

    // ===================== Protected Endpoints (require JWT) =====================

    @GetMapping("/roles")
    @Operation(summary = "List all roles", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getAllRoles() {
        List<RoleResponse> roles = roleRepository.findAll().stream()
                .map(AuthMapper::toRoleResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Roles retrieved", roles));
    }

    @GetMapping("/roles/{name}")
    @Operation(summary = "Get role by name", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<RoleResponse>> getRoleByName(@PathVariable String name) {
        return roleRepository.findByName(name.toUpperCase())
                .map(role -> ResponseEntity.ok(ApiResponse.success("Role found", AuthMapper.toRoleResponse(role))))
                .orElseThrow(() -> new EntityNotFoundException("Role not found: " + name));
    }

    @GetMapping("/users")
    @Operation(summary = "List all users", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers() {
        List<UserResponse> users = userRepository.findAll().stream()
                .map(AuthMapper::toUserResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Users retrieved", users));
    }

    @GetMapping("/users/{id}")
    @Operation(summary = "Get user by ID", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(user -> ResponseEntity.ok(ApiResponse.success("User found", AuthMapper.toUserResponse(user))))
                .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + id));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user profile", security = @SecurityRequirement(name = "bearerAuth"),
            description = "Returns the profile of the currently authenticated user")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(
            @org.springframework.security.core.annotation.AuthenticationPrincipal
            org.springframework.security.core.userdetails.UserDetails userDetails) {
        return userRepository.findByUsername(userDetails.getUsername())
                .map(user -> ResponseEntity.ok(ApiResponse.success("Current user", AuthMapper.toUserResponse(user))))
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
    }
}
