package com.web.labportalbackend.lab.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter @Setter
@Schema(description = "Create laboratory request")
public class CreateLabRequest {
    @NotBlank(message = "Lab name is required") @Size(min = 3, max = 100, message = "Lab name must be 3-100 characters")
    @Schema(description = "Laboratory name", example = "Physics Lab A") private String labName;
    @Size(max = 500, message = "Description must not exceed 500 characters")
    @Schema(description = "Laboratory description") private String description;
    @NotBlank(message = "Location is required") @Size(min = 3, max = 200, message = "Location must be 3-200 characters")
    @Schema(description = "Physical location", example = "Building A, Floor 3") private String location;
    @Min(value = 1, message = "Capacity must be at least 1")
    @Schema(description = "Maximum capacity", example = "30") private Integer capacity;
    @Size(max = 100, message = "Department must not exceed 100 characters")
    @Schema(description = "Department name", example = "Physics") private String department;

    @DecimalMin(value = "-90.0", message = "Check-in latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Check-in latitude must be between -90 and 90")
    @Schema(description = "Optional latitude captured from the lab registration device", example = "10.762622", nullable = true)
    private BigDecimal checkinLatitude;

    @DecimalMin(value = "-180.0", message = "Check-in longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Check-in longitude must be between -180 and 180")
    @Schema(description = "Optional longitude captured from the lab registration device", example = "106.660172", nullable = true)
    private BigDecimal checkinLongitude;
}
