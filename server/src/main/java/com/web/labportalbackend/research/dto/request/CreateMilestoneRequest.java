package com.web.labportalbackend.research.dto.request;

import com.web.labportalbackend.research.enums.MilestoneStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CreateMilestoneRequest {

    private Long projectId;

    private Long groupId;

    @NotBlank(message = "Milestone title is required")
    @Size(min = 3, max = 200, message = "Milestone title must be between 3 and 200 characters")
    private String title;

    @Size(max = 4000, message = "Milestone description must not exceed 4000 characters")
    private String description;

    private Long assignedToStudentId;

    private LocalDate deadline;

    private MilestoneStatus status = MilestoneStatus.NOT_STARTED;

    @Min(value = 0, message = "Progress percent must be at least 0")
    @Max(value = 100, message = "Progress percent must not exceed 100")
    private Integer progressPercent = 0;

    @Size(max = 1000, message = "Evidence URL must not exceed 1000 characters")
    private String evidenceUrl;

    @Size(max = 4000, message = "Manager comment must not exceed 4000 characters")
    private String managerComment;
}
