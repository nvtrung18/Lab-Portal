package com.web.labportalbackend.research.dto.request;

import com.web.labportalbackend.research.enums.MilestoneStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CreateMilestoneRequest {

    @NotNull(message = "Project ID is required")
    private Long projectId;

    @NotBlank(message = "Milestone name is required")
    @Size(max = 200, message = "Milestone name must not exceed 200 characters")
    private String name;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "End date is required")
    private LocalDate endDate;

    private MilestoneStatus status = MilestoneStatus.PLANNED;
}
