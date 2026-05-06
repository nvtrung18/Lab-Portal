package com.web.labportalbackend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO for updating user profile information.
 * Only allows updates to fullName and phone, not email or username.
 */
@Getter
@Setter
@Builder
@Schema(description = "Request to update user profile")
public class UpdateProfileRequest {

    @Schema(description = "Full name", example = "John Doe")
    @NotBlank(message = "Full name cannot be blank")
    @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
    private String fullName;

    @Schema(description = "Phone number", example = "+84901234567")
    @Pattern(
            regexp = "^[+]?[0-9]{10,15}$",
            message = "Phone number must be 10-15 digits, optionally starting with +"
    )
    private String phone;
}

