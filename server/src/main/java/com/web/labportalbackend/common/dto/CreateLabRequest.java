package com.web.labportalbackend.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Request payload for creating a new laboratory.
 */
@Getter
@Setter
@Schema(description = "Create laboratory request")
public class CreateLabRequest {

    @NotBlank(message = "Lab name is required")
    @Size(min = 3, max = 100, message = "Lab name must be 3-100 characters")
    @Schema(description = "Laboratory name", example = "Physics Lab A")
    private String labName;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    @Schema(description = "Laboratory description", example = "Advanced physics experiments lab")
    private String description;

    @NotBlank(message = "Location is required")
    @Size(min = 3, max = 200, message = "Location must be 3-200 characters")
    @Schema(description = "Physical location", example = "Building A, Floor 3")
    private String location;

    @Min(value = 1, message = "Capacity must be at least 1")
    @Schema(description = "Maximum capacity", example = "30")
    private Integer capacity;

    @Size(max = 100, message = "Department must not exceed 100 characters")
    @Schema(description = "Department name", example = "Physics")
    private String department;
}
