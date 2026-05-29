package com.web.labportalbackend.auth.service;

import com.web.labportalbackend.auth.dto.*;
import java.util.List;

/**
 * Service interface for authentication operations.
 */
public interface AuthService {
    AuthResponse login(LoginRequest request);
    AuthEmailResponse sendRegistrationCode(RegisterSendCodeRequest request);
    RegisterVerifyCodeResponse verifyRegistrationCode(VerifyRegisterRequest request);
    AuthEmailResponse register(RegisterRequest request);
    AuthEmailResponse sendPasswordResetCode(ForgotPasswordRequest request);
    PasswordResetVerifyResponse verifyPasswordResetCode(VerifyRegisterRequest request);
    void resetPassword(ResetPasswordRequest request);
    AuthResponse refreshToken(String refreshToken);
    List<RoleResponse> getAllRoles();
    List<UserResponse> getAllUsers();
    UserResponse getUserById(Long id);
    UserResponse updateUserRoles(Long id, UpdateUserRolesRequest request);
    UpdateUserRoleResponse patchUserRole(Long id, UpdateUserRoleRequest request);
    UserResponse banUser(Long id);
    UserResponse unbanUser(Long id);
    List<AssignableManagerResponse> getAssignableManagers();
}
