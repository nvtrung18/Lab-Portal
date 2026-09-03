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

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication, user and role management")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/login")
    @Operation(summary = "User login", description = "Authenticate with email/username and password, returns JWT tokens")
    public ResponseEntity<Response<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse authResponse = authService.login(request);
        return ResponseEntity.ok(Response.ok("Login successful", authResponse));
    }

    @PostMapping("/google")
    @Operation(summary = "Google sign-in", description = "Verify a Google ID token, then sign in or create a STUDENT account")
    public ResponseEntity<Response<AuthResponse>> loginWithGoogle(@Valid @RequestBody GoogleAuthRequest request) {
        AuthResponse authResponse = authService.loginWithGoogle(request);
        return ResponseEntity.ok(Response.ok("Google sign-in successful", authResponse));
    }

    @PostMapping("/register/send-code")
    @Operation(summary = "Send registration code", description = "Send registration OTP to email without creating a user")
    public ResponseEntity<Response<AuthEmailResponse>> sendRegistrationCode(@Valid @RequestBody RegisterSendCodeRequest request) {
        AuthEmailResponse result = authService.sendRegistrationCode(request);
        return ResponseEntity.ok(Response.ok(result.getMessage(), result));
    }

    @PostMapping("/register/verify-code")
    @Operation(summary = "Verify registration code", description = "Verify registration OTP and issue a temporary token")
    public ResponseEntity<Response<RegisterVerifyCodeResponse>> verifyRegistrationCode(@Valid @RequestBody VerifyRegisterRequest request) {
        RegisterVerifyCodeResponse result = authService.verifyRegistrationCode(request);
        return ResponseEntity.ok(Response.ok(result.getMessage(), result));
    }

    @PostMapping("/register")
    @Operation(summary = "Complete user registration", description = "Create a STUDENT account after email verification")
    public ResponseEntity<Response<AuthEmailResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthEmailResponse result = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Response.ok(result.getMessage(), result));
    }

    @PostMapping("/forgot-password/send-code")
    @Operation(summary = "Forgot password", description = "Send password reset code if email exists")
    public ResponseEntity<Response<AuthEmailResponse>> sendPasswordResetCode(@Valid @RequestBody ForgotPasswordRequest request) {
        AuthEmailResponse result = authService.sendPasswordResetCode(request);
        return ResponseEntity.ok(Response.ok(result.getMessage(), result));
    }

    @PostMapping("/forgot-password/verify-code")
    @Operation(summary = "Verify password reset code", description = "Verify password reset OTP and issue a temporary reset token")
    public ResponseEntity<Response<PasswordResetVerifyResponse>> verifyPasswordResetCode(@Valid @RequestBody VerifyRegisterRequest request) {
        PasswordResetVerifyResponse result = authService.verifyPasswordResetCode(request);
        return ResponseEntity.ok(Response.ok(result.getMessage(), result));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password", description = "Reset password using a temporary reset token")
    public ResponseEntity<Response<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(Response.ok("Đặt lại mật khẩu thành công."));
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
