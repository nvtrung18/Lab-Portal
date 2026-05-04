package com.web.labportalbackend.auth.controller;

import com.web.labportalbackend.auth.dto.RoleResponse;
import com.web.labportalbackend.auth.dto.UserResponse;
import com.web.labportalbackend.auth.mapper.AuthMapper;
import com.web.labportalbackend.auth.repository.RoleRepository;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.persistence.EntityNotFoundException;
import java.util.List;

/**
 * Authentication & user management endpoints.
 * <p>
 * Day 2: Read-only endpoints to verify auth schema, seed data, and Swagger schemas.
 * JWT login/register logic will be added in Day 3.
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication, user and role management")
public class AuthController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    // ===================== Health =====================

    @GetMapping("/health")
    @Operation(summary = "Auth service health check")
    public ResponseEntity<ApiResponse<String>> health() {
        return ResponseEntity.ok(ApiResponse.success("Auth service is healthy", "UP"));
    }

    // ===================== Roles =====================

    @GetMapping("/roles")
    @Operation(summary = "List all roles", description = "Retrieve all available roles in the system")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> getAllRoles() {
        List<RoleResponse> roles = roleRepository.findAll().stream()
                .map(AuthMapper::toRoleResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Roles retrieved", roles));
    }

    @GetMapping("/roles/{name}")
    @Operation(summary = "Get role by name", description = "Retrieve a specific role by its name")
    public ResponseEntity<ApiResponse<RoleResponse>> getRoleByName(@PathVariable String name) {
        return roleRepository.findByName(name.toUpperCase())
                .map(role -> ResponseEntity.ok(ApiResponse.success("Role found", AuthMapper.toRoleResponse(role))))
                .orElseThrow(() -> new EntityNotFoundException("Role not found: " + name));
    }

    // ===================== Users =====================

    @GetMapping("/users")
    @Operation(summary = "List all users", description = "Retrieve all users (admin only in future)")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getAllUsers() {
        List<UserResponse> users = userRepository.findAll().stream()
                .map(AuthMapper::toUserResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Users retrieved", users));
    }

    @GetMapping("/users/{id}")
    @Operation(summary = "Get user by ID", description = "Retrieve a specific user by ID")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(user -> ResponseEntity.ok(ApiResponse.success("User found", AuthMapper.toUserResponse(user))))
                .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + id));
    }

    // ===================== Auth stubs (Day 3) =====================

    @PostMapping("/login")
    @Operation(summary = "User login", description = "Authenticate user credentials and return JWT (Day 3)")
    public ResponseEntity<ApiResponse<String>> login() {
        return ResponseEntity.ok(ApiResponse.success("Login endpoint ready — JWT to be implemented in Day 3"));
    }

    @PostMapping("/register")
    @Operation(summary = "User registration", description = "Register a new user account (Day 3)")
    public ResponseEntity<ApiResponse<String>> register() {
        return ResponseEntity.ok(ApiResponse.success("Register endpoint ready — implementation pending Day 3"));
    }

    @PostMapping("/refresh-token")
    @Operation(summary = "Refresh token", description = "Refresh JWT token (Day 3)")
    public ResponseEntity<ApiResponse<String>> refreshToken() {
        return ResponseEntity.ok(ApiResponse.success("Refresh token endpoint ready — JWT to be implemented in Day 3"));
    }
}
