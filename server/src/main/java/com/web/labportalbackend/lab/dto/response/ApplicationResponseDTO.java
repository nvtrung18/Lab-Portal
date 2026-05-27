package com.web.labportalbackend.lab.dto.response;

import com.web.labportalbackend.common.enums.ApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import java.time.Instant;

@Getter @Builder
@Schema(description = "Application information")
public class ApplicationResponseDTO {
    @Schema(description = "Application ID", example = "1") private Long id;
    @Schema(description = "Applicant user ID", example = "5") private Long userId;
    @Schema(description = "Applicant display name", example = "Nguyen Van A") private String applicantName;
    @Schema(description = "Applicant email", example = "student@labportal.com") private String applicantEmail;
    @Schema(description = "Target laboratory ID", example = "2") private Long labId;
    @Schema(description = "Laboratory name", example = "Physics Lab A") private String labName;
    @Schema(description = "CV URL submitted by the applicant") private String cvUrl;
    @Schema(description = "Uploaded CV file URL") private String cvFileUrl;
    @Schema(description = "Uploaded CV original file name") private String cvFileName;
    @Schema(description = "Uploaded CV content type") private String cvContentType;
    @Schema(description = "Uploaded CV file size in bytes") private Long cvSize;
    @Schema(description = "Application status", example = "PENDING") private ApplicationStatus status;
    @Schema(description = "Submission timestamp") private Instant createdAt;
    @Schema(description = "Last update timestamp") private Instant updatedAt;
}
