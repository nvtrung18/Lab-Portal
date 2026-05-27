package com.web.labportalbackend.research.dto.request;

import com.web.labportalbackend.research.enums.ManagerReportDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ManagerReviewReportRequest {

    @NotNull(message = "Review decision is required")
    private ManagerReportDecision decision;

    @NotBlank(message = "Review comment is required")
    @Size(max = 5000, message = "Review comment must not exceed 5000 characters")
    private String comment;
}
