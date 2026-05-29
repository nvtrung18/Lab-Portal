package com.web.labportalbackend.research.dto.response;

import com.web.labportalbackend.research.enums.TaskStatus;
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
    private String milestoneTitle;
    private String title;
    private String description;
    private String latestReportStatus;
    private Long assignedToStudentId;
    private String assignedToStudentName;
    private String assignedToStudentEmail;
    private LocalDate deadline;
    private TaskStatus status;
    private Integer progressPercent;
    private Instant createdAt;
    private Instant updatedAt;
}
