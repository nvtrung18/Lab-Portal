package com.web.labportalbackend.common.dto;

import com.web.labportalbackend.auth.dto.UserResponse;
import com.web.labportalbackend.common.enums.LabStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * DTO for returning laboratory information.
 */
@Getter
@Builder
@Schema(description = "Laboratory information")
public class LabDTO {

    @Schema(description = "Laboratory ID", example = "1")
    private Long id;

    @Schema(description = "Laboratory name", example = "Physics Lab A")
    private String labName;

    @Schema(description = "Laboratory description")
    private String description;

    @Schema(description = "Physical location", example = "Building A, Floor 3")
    private String location;

    @Schema(description = "Maximum capacity", example = "30")
    private Integer capacity;

    @Schema(description = "Department name", example = "Physics")
    private String department;

    @Schema(description = "Lab status", example = "AVAILABLE")
    private LabStatus status;

    @Schema(description = "Lab manager information")
    private UserResponse manager;

    @Schema(description = "Laboratory creation timestamp")
    private Instant createdAt;

    @Schema(description = "Last update timestamp")
    private Instant updatedAt;
}
