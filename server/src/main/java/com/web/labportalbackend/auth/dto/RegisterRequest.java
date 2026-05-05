package com.web.labportalbackend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Registration request payload.
 */
@Getter
@Setter
@Schema(description = "User registration request")
public class RegisterRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Schema(description = "Email address", example = "user@labportal.com")
    private String email;

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be 3-50 characters")
    @Schema(description = "Username", example = "johndoe")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 100, message = "Password must be 6-100 characters")
    @Schema(description = "Password (min 6 chars)", example = "password123")
    private String password;

    @Size(max = 100, message = "Full name must not exceed 100 characters")
    @Schema(description = "Full name", example = "John Doe")
    private String fullName;

    @Schema(description = "Phone number", example = "+84901234567")
    private String phone;
}
