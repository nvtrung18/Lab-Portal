package com.web.labportalbackend.research.dto.request;

import com.web.labportalbackend.research.enums.TaskPriority;
import com.web.labportalbackend.research.enums.TaskType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CreateResearchTaskRequest {

    @NotNull(message = "Project ID is required")
    private Long projectId;

    private Long groupId;
    private Long milestoneId;
    private Long parentTaskId;

    @NotBlank(message = "Task title is required")
    @Size(max = 200, message = "Task title must not exceed 200 characters")
    private String title;

    private String description;
    private Long assigneeId;
    private TaskPriority priority;
    private TaskType type;
    private LocalDate dueDate;
}
