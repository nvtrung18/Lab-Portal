package com.web.labportalbackend.research.dto.response;

import com.web.labportalbackend.research.enums.TaskPriority;
import com.web.labportalbackend.research.enums.TaskProposalStatus;
import com.web.labportalbackend.research.enums.TaskType;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record TaskProposalListItemResponse(
        Long id, Long proposedById, Long projectId, Long groupId, Long milestoneId, Long parentTaskId,
        String title, String description, TaskPriority priority, TaskType type, LocalDate dueDate,
        Boolean assistedByAi, Long aiActionSuggestionId, TaskProposalStatus status, Long reviewedById,
        String reason, Instant reviewedAt, Instant createdAt, Instant updatedAt, boolean canReview
) {}
