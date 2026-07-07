package com.web.labportalbackend.research.mapper;

import com.web.labportalbackend.research.dto.response.TaskResponse;
import com.web.labportalbackend.research.entity.TaskEntity;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TaskMapper {

    public static TaskResponse toResponse(TaskEntity task) {
        return toResponse(task, null, null, null, null);
    }

    public static TaskResponse toResponse(TaskEntity task, Long projectId) {
        return toResponse(task, projectId, null, null, null);
    }

    public static TaskResponse toResponse(TaskEntity task, Long projectId, Long groupId, String milestoneTitle, String latestReportStatus) {
        return TaskResponse.builder()
                .id(task.getId())
                .milestoneId(task.getMilestoneId())
                .projectId(task.getProjectId() != null ? task.getProjectId() : projectId)
                .groupId(task.getGroupId() != null ? task.getGroupId() : groupId)
                .parentTaskId(task.getParentTaskId())
                .epicId(task.getEpicId())
                .milestoneTitle(milestoneTitle)
                .title(task.getTitle())
                .description(task.getDescription())
                .assignedToStudentId(task.getAssignedToStudent() != null
                        ? task.getAssignedToStudent().getId()
                        : task.getAssigneeId())
                .assignedToStudentName(task.getAssignedToStudent() != null
                        ? task.getAssignedToStudent().getFullName()
                        : null)
                .assignedToStudentEmail(task.getAssignedToStudent() != null
                        ? task.getAssignedToStudent().getEmail()
                        : null)
                .deadline(task.getDeadline())
                .dueDate(task.getDueDate() != null ? task.getDueDate() : task.getDeadline())
                .status(task.getStatus())
                .priority(task.getPriority())
                .type(task.getType())
                .blockedReason(task.getBlockedReason())
                .createdBy(task.getCreatedBy())
                .progressPercent(task.getProgressPercent() != null ? task.getProgressPercent() : 0)
                .latestReportStatus(latestReportStatus)
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }
}
