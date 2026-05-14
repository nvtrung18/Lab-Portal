package com.web.labportalbackend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Login request payload.
 */
@Getter
@Setter
@Schema(description = "Login request")
public class LoginRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Schema(description = "Email address", example = "admin@labportal.com")
    private String email;

    @NotBlank(message = "Password is required")
    @Schema(description = "Password", example = "admin123")
    private String password;
}
