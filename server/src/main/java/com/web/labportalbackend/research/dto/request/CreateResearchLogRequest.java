package com.web.labportalbackend.research.dto.request;

import com.web.labportalbackend.research.enums.ResearchLogVisibility;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CreateResearchLogRequest {

    @NotNull(message = "Project ID is required")
    private Long projectId;

    private Long groupId;

    private Long milestoneId;

    private Long taskId;

    @NotNull(message = "Work date is required")
    private LocalDate workDate;

    @Min(value = 0, message = "Duration minutes must be greater than or equal to 0")
    private Integer durationMinutes = 0;

    @NotBlank(message = "Content is required")
    private String content;

    private String result;

    private String problem;

    private String nextPlan;

    @Size(max = 1000, message = "Evidence link must not exceed 1000 characters")
    private String evidenceLink;

    private ResearchLogVisibility visibility = ResearchLogVisibility.GROUP;
}
