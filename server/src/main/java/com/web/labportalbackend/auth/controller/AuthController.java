package com.web.labportalbackend.auth.controller;

import com.web.labportalbackend.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints skeleton.
 * Actual JWT logic will be implemented in Day 2.
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "User authentication and authorization")
public class AuthController {

    @GetMapping("/health")
    @Operation(summary = "Auth service health check")
    public ResponseEntity<ApiResponse<String>> health() {
        return ResponseEntity.ok(ApiResponse.success("Auth service is healthy", "UP"));
    }

    @PostMapping("/login")
    @Operation(summary = "User login", description = "Authenticate user credentials and return JWT (Day 2)")
    public ResponseEntity<ApiResponse<String>> login() {
        return ResponseEntity.ok(ApiResponse.success("Login endpoint ready — JWT to be implemented"));
    }

    @PostMapping("/register")
    @Operation(summary = "User registration", description = "Register a new user account (Day 2)")
    public ResponseEntity<ApiResponse<String>> register() {
        return ResponseEntity.ok(ApiResponse.success("Register endpoint ready — implementation pending"));
    }

    @PostMapping("/refresh-token")
    @Operation(summary = "Refresh token", description = "Refresh JWT token (Day 2)")
    public ResponseEntity<ApiResponse<String>> refreshToken() {
        return ResponseEntity.ok(ApiResponse.success("Refresh token endpoint ready — JWT to be implemented"));
    }
}
