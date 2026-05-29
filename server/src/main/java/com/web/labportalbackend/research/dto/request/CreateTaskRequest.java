package com.web.labportalbackend.research.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CreateTaskRequest {

    private Long milestoneId;

    @NotBlank(message = "Task title is required")
    @Size(max = 200, message = "Task title must not exceed 200 characters")
    private String title;

    @Size(max = 4000, message = "Task description must not exceed 4000 characters")
    private String description;

    private Long assignedToStudentId;

    private LocalDate deadline;
}
