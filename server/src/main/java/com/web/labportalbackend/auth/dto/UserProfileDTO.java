package com.web.labportalbackend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * DTO for returning user profile information (without password).
 * Used for both GET /api/users/me and PUT /api/users/me responses.
 */
@Getter
@Builder
@Schema(description = "User profile information")
public class UserProfileDTO {

    @Schema(description = "User ID", example = "1")
    private Long id;

    @Schema(description = "Email address", example = "user@labportal.com")
    private String email;

    @Schema(description = "Username", example = "john_doe")
    private String username;

    @Schema(description = "Full name", example = "John Doe")
    private String fullName;

    @Schema(description = "Phone number", example = "+84901234567")
    private String phone;

    @Schema(description = "Account status", example = "ACTIVE")
    private String status;

    @Schema(description = "Assigned role names", example = "[\"STUDENT\"]")
    private Set<String> roles;

    @Schema(description = "Current user's laboratory memberships")
    private List<UserMembershipDTO> memberships;

    @Schema(description = "Laboratory managed by current user when user is LAB_MANAGER")
    private ManagedLabDTO managedLab;

    @Schema(description = "Managed laboratory ID when user is LAB_MANAGER", example = "1")
    private Long managedLabId;

    @Schema(description = "Account creation timestamp")
    private Instant createdAt;

    @Schema(description = "Last update timestamp")
    private Instant updatedAt;
}

