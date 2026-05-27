package com.web.labportalbackend.research.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LeaderReviewReportRequest {

    @NotBlank(message = "Review note is required")
    @Size(max = 5000, message = "Review note must not exceed 5000 characters")
    private String note;
}
