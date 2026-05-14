package com.web.labportalbackend.lab.dto.request;

import com.web.labportalbackend.common.enums.LabStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@Schema(description = "Update laboratory request")
public class UpdateLabRequest {
    @Size(min = 3, max = 100, message = "Lab name must be 3-100 characters") private String labName;
    @Size(max = 500, message = "Description must not exceed 500 characters") private String description;
    @Size(min = 3, max = 200, message = "Location must be 3-200 characters") private String location;
    @Min(value = 1, message = "Capacity must be at least 1") private Integer capacity;
    @Size(max = 100, message = "Department must not exceed 100 characters") private String department;
    private LabStatus status;
}
