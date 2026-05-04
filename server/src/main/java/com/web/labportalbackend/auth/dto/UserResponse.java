package com.web.labportalbackend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Set;

/**
 * DTO for returning user information (without password).
 */
@Getter
@Builder
@Schema(description = "User profile information")
public class UserResponse {

    @Schema(description = "User ID", example = "1")
    private Long id;

    @Schema(description = "Email address", example = "admin@labportal.com")
    private String email;

    @Schema(description = "Username", example = "admin")
    private String username;

    @Schema(description = "Full name", example = "System Administrator")
    private String fullName;

    @Schema(description = "Phone number", example = "+84901234567")
    private String phone;

    @Schema(description = "Account status", example = "ACTIVE")
    private String status;

    @Schema(description = "Assigned role names", example = "[\"ADMIN\"]")
    private Set<String> roles;

    @Schema(description = "Account creation timestamp")
    private Instant createdAt;

    @Schema(description = "Last update timestamp")
    private Instant updatedAt;
}
