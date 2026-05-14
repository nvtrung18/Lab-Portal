package com.web.labportalbackend.lab.dto.request;

import com.web.labportalbackend.common.enums.ApplicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter @Setter @Builder @AllArgsConstructor
@Schema(description = "Request body for reviewing an application")
public class ReviewApplicationDTO {
    @NotNull(message = "Status is required") @Schema(description = "New application status", example = "APPROVED") private ApplicationStatus status;
}
