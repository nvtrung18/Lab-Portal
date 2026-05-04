package com.web.labportalbackend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * DTO for returning role information.
 */
@Getter
@Builder
@Schema(description = "Role information")
public class RoleResponse {

    @Schema(description = "Role ID", example = "1")
    private Long id;

    @Schema(description = "Role name", example = "ADMIN")
    private String name;

    @Schema(description = "Role description", example = "System administrator with full access")
    private String description;

    @Schema(description = "Creation timestamp")
    private Instant createdAt;
}
