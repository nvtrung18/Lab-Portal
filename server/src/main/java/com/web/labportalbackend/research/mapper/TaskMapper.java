package com.web.labportalbackend.research.mapper;

import com.web.labportalbackend.research.dto.response.TaskResponse;
import com.web.labportalbackend.research.entity.TaskEntity;
import com.web.labportalbackend.research.enums.TaskStatus;
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
                .projectId(projectId)
                .groupId(groupId)
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
                .status(toPublicStatus(task.getStatus()))
                .progressPercent(task.getProgressPercent() != null ? task.getProgressPercent() : 0)
                .latestReportStatus(latestReportStatus)
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    private static TaskStatus toPublicStatus(TaskStatus status) {
        if (status == TaskStatus.IN_PROGRESS) {
            return TaskStatus.DOING;
        }
        if (status == TaskStatus.REVIEW) {
            return TaskStatus.WAITING_REVIEW;
        }
        return status;
    }
}
