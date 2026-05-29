package com.web.labportalbackend.research.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReplaceReportRequest {

    @NotBlank(message = "Report title is required")
    @Size(max = 200, message = "Report title must not exceed 200 characters")
    private String title;

    @NotBlank(message = "Completed work is required")
    private String contentDone;

    @NotBlank(message = "Result is required")
    private String result;

    @NotBlank(message = "Difficulty is required")
    private String difficulty;

    @NotBlank(message = "Next plan is required")
    private String nextPlan;

    @NotBlank(message = "Self assessment is required")
    private String selfAssessment;

    @Size(max = 1000, message = "Evidence link must not exceed 1000 characters")
    private String evidenceLink;
}
