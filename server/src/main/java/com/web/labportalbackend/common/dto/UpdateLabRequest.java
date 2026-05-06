package com.web.labportalbackend.common.dto;

import com.web.labportalbackend.common.enums.LabStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Request payload for updating laboratory details.
 */
@Getter
@Setter
@Schema(description = "Update laboratory request")
public class UpdateLabRequest {

    @Size(min = 3, max = 100, message = "Lab name must be 3-100 characters")
    @Schema(description = "Laboratory name", example = "Physics Lab A Updated")
    private String labName;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    @Schema(description = "Laboratory description")
    private String description;

    @Size(min = 3, max = 200, message = "Location must be 3-200 characters")
    @Schema(description = "Physical location")
    private String location;

    @Min(value = 1, message = "Capacity must be at least 1")
    @Schema(description = "Maximum capacity")
    private Integer capacity;

    @Size(max = 100, message = "Department must not exceed 100 characters")
    @Schema(description = "Department name")
    private String department;

    @Schema(description = "Laboratory status", example = "AVAILABLE")
    private LabStatus status;
}
