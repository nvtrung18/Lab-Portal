package com.web.labportalbackend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Login request payload. Accepts either email or username.
 */
@Getter
@Setter
@Schema(description = "Login request")
public class LoginRequest {

    @NotBlank(message = "Username or email is required")
    @Schema(description = "Username or email", example = "admin")
    private String usernameOrEmail;

    @NotBlank(message = "Password is required")
    @Schema(description = "Password", example = "admin123")
    private String password;
}
