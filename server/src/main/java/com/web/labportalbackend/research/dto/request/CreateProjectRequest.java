package com.web.labportalbackend.research.dto.request;

import com.web.labportalbackend.research.enums.ProjectStatus;
import com.web.labportalbackend.research.enums.ResearchPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CreateProjectRequest {

    @NotNull(message = "Group ID is required")
    private Long groupId;

    @Size(max = 50, message = "Project code must not exceed 50 characters")
    private String code;

    @NotBlank(message = "Title is required")
    @Size(min = 3, max = 200, message = "Title must be between 3 and 200 characters")
    private String title;

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;

    @Size(max = 2000, message = "Objective must not exceed 2000 characters")
    private String objective;

    private ProjectStatus status = ProjectStatus.DRAFT;

    private LocalDate startDate;

    private LocalDate endDate;

    private LocalDate expectedEndDate;

    private ResearchPriority priority = ResearchPriority.MEDIUM;

    @Size(max = 2000, message = "Required products must not exceed 2000 characters")
    private String requiredProducts;

    @Size(max = 2000, message = "Evaluation criteria must not exceed 2000 characters")
    private String evaluationCriteria;
}
