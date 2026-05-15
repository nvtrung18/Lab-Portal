package com.web.labportalbackend.auth.service;

import com.web.labportalbackend.auth.dto.*;
import java.util.List;

/**
 * Service interface for authentication operations.
 */
public interface AuthService {
    AuthResponse login(LoginRequest request);
    AuthResponse register(RegisterRequest request);
    AuthResponse refreshToken(String refreshToken);
    List<RoleResponse> getAllRoles();
    List<UserResponse> getAllUsers();
    UserResponse getUserById(Long id);
    UserResponse updateUserRoles(Long id, java.util.Set<String> roles);
    UserResponse banUser(Long id);
    UserResponse unbanUser(Long id);
}
