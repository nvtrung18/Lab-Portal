package com.web.labportalbackend.common.dto;

import com.web.labportalbackend.common.enums.ApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO for reviewing an application (approve or reject).
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@Schema(description = "Request body for reviewing an application")
public class ReviewApplicationDTO {

    @NotNull(message = "Status is required")
    @Schema(description = "New application status (APPROVED or REJECTED)", example = "APPROVED")
    private ApplicationStatus status;
}
