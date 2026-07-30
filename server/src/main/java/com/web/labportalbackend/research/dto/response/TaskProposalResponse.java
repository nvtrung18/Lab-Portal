package com.web.labportalbackend.research.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.web.labportalbackend.research.enums.TaskPriority;
import com.web.labportalbackend.research.enums.TaskProposalStatus;
import com.web.labportalbackend.research.enums.TaskType;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public class TaskProposalResponse {
    private Long id;
    private Long proposedById;
    private Long projectId;
    private Long groupId;
    private Long milestoneId;
    private Long parentTaskId;
    private String title;
    private String description;
    private TaskPriority priority;
    private TaskType type;
    private LocalDate dueDate;
    private Boolean assistedByAi;
    private Long aiActionSuggestionId;
    private TaskProposalStatus status;
    private Instant createdAt;
    private Instant updatedAt;
}
