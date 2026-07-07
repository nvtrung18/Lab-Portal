package com.web.labportalbackend.research.dto.response;

import com.web.labportalbackend.research.enums.TaskPriority;
import com.web.labportalbackend.research.enums.TaskStatus;
import com.web.labportalbackend.research.enums.TaskType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Builder
public class TaskResponse {
    private Long id;
    private Long milestoneId;
    private Long projectId;
    private Long groupId;
    private Long parentTaskId;
    private Long epicId;
    private String milestoneTitle;
    private String title;
    private String description;
    private String latestReportStatus;
    private Long assignedToStudentId;
    private String assignedToStudentName;
    private String assignedToStudentEmail;
    private LocalDate deadline;
    private LocalDate dueDate;
    private TaskStatus status;
    private TaskPriority priority;
    private TaskType type;
    private String blockedReason;
    private Long createdBy;
    private Integer progressPercent;
    private Instant createdAt;
    private Instant updatedAt;
}
