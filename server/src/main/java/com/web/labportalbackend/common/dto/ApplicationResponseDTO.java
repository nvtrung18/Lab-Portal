package com.web.labportalbackend.common.dto;

import com.web.labportalbackend.common.enums.ApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * DTO for application responses.
 * Contains application details for API responses.
 */
@Getter
@Builder
@Schema(description = "Application information")
public class ApplicationResponseDTO {

    @Schema(description = "Application ID", example = "1")
    private Long id;

    @Schema(description = "Applicant user ID", example = "5")
    private Long userId;

    @Schema(description = "Target laboratory ID", example = "2")
    private Long labId;

    @Schema(description = "Laboratory name", example = "Physics Lab A")
    private String labName;

    @Schema(description = "CV file URL/path", example = "https://storage.example.com/cvs/user5_cv.pdf")
    private String cvUrl;

    @Schema(description = "Application status", example = "PENDING")
    private ApplicationStatus status;

    @Schema(description = "Application submission timestamp")
    private Instant createdAt;

    @Schema(description = "Last update timestamp")
    private Instant updatedAt;
}
