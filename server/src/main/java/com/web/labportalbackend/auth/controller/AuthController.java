package com.web.labportalbackend.auth.controller;

import com.web.labportalbackend.auth.dto.*;
import com.web.labportalbackend.auth.service.AuthService;
import com.web.labportalbackend.auth.service.UserService;
import com.web.labportalbackend.common.dto.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Authentication & user management endpoints.
 * <p>
 * Thin controller — delegates all logic to service layer.
 * NEVER accesses Repository directly.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication, user and role management")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    // ===================== Public Auth Endpoints =====================

    @PostMapping("/login")
    @Operation(summary = "User login", description = "Authenticate with email/username and password, returns JWT tokens")
    public ResponseEntity<Response<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse authResponse = authService.login(request);
        return ResponseEntity.ok(Response.ok("Login successful", authResponse));
    }

    @PostMapping("/register")
    @Operation(summary = "User registration", description = "Register a new user account with STUDENT role")
    public ResponseEntity<Response<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse authResponse = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.ok("Registration successful", authResponse));
    }

    @PostMapping("/refresh-token")
    @Operation(summary = "Refresh token", description = "Get new access token using a valid refresh token")
    public ResponseEntity<Response<AuthResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse authResponse = authService.refreshToken(request.getRefreshToken());
        return ResponseEntity.ok(Response.ok("Token refreshed", authResponse));
    }

    @GetMapping("/health")
    @Operation(summary = "Auth service health check")
    public ResponseEntity<Response<String>> health() {
        return ResponseEntity.ok(Response.ok("Auth service is healthy", "UP"));
    }

    // ===================== Protected Endpoints =====================

    @GetMapping("/me")
    @Operation(summary = "Get current user profile", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Response<UserProfileDTO>> getCurrentUser(
            @AuthenticationPrincipal UserDetails userDetails) {
        UserProfileDTO profile = userService.getCurrentUser();
        return ResponseEntity.ok(Response.ok("Current user", profile));
    }

    @PutMapping("/me")
    @Operation(summary = "Update current user profile", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Response<UserProfileDTO>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request) {
        UserProfileDTO profile = userService.updateProfile(request);
        return ResponseEntity.ok(Response.ok("Profile updated", profile));
    }

    @GetMapping("/roles")
    @Operation(summary = "List all roles", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Response<List<RoleResponse>>> getAllRoles() {
        List<RoleResponse> roles = authService.getAllRoles();
        return ResponseEntity.ok(Response.ok("Roles retrieved", roles));
    }

    @GetMapping("/users")
    @Operation(summary = "List all users", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Response<List<UserResponse>>> getAllUsers() {
        List<UserResponse> users = authService.getAllUsers();
        return ResponseEntity.ok(Response.ok("Users retrieved", users));
    }

    @GetMapping("/users/{id}")
    @Operation(summary = "Get user by ID", security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Response<UserResponse>> getUserById(@PathVariable Long id) {
        UserResponse user = authService.getUserById(id);
        return ResponseEntity.ok(Response.ok("User found", user));
    }
}
