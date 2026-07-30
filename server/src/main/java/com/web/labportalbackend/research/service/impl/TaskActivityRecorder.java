package com.web.labportalbackend.research.service.impl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.research.entity.TaskActivityEntity;
import com.web.labportalbackend.research.entity.TaskEntity;
import com.web.labportalbackend.research.enums.TaskAuditAction;
import com.web.labportalbackend.research.enums.TaskPriority;
import com.web.labportalbackend.research.enums.TaskStatus;
import com.web.labportalbackend.research.enums.TaskType;
import com.web.labportalbackend.research.repository.TaskActivityRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Objects;

@Component
public class TaskActivityRecorder {

    private static final int SCHEMA_VERSION = 1;

    private final TaskActivityRepository taskActivityRepository;
    private final ObjectMapper objectMapper;

    public TaskActivityRecorder(TaskActivityRepository taskActivityRepository, ObjectMapper objectMapper) {
        this.taskActivityRepository = taskActivityRepository;
        this.objectMapper = objectMapper.copy()
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    public TaskSnapshot capture(TaskEntity task) {
        Objects.requireNonNull(task, "task is required");
        return new TaskSnapshot(
                SCHEMA_VERSION,
                task.getProjectId(),
                task.getGroupId(),
                task.getMilestoneId(),
                task.getParentTaskId(),
                task.getAssigneeId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority(),
                task.getType(),
                task.getDueDate(),
                task.getBlockedReason(),
                task.getProgressPercent()
        );
    }

    public void recordCreation(TaskEntity task, User actor) {
        record(task, actor, TaskAuditAction.TASK_CREATED, null, capture(task));
    }

    public void recordMutation(TaskSnapshot before, TaskEntity afterTask, User actor) {
        TaskSnapshot after = capture(afterTask);
        if (before.equals(after)) {
            return;
        }
        record(afterTask, actor, actionFor(before, after), before, after);
    }

    private TaskAuditAction actionFor(TaskSnapshot before, TaskSnapshot after) {
        if (!Objects.equals(before.status(), after.status())) {
            return switch (after.status()) {
                case BLOCKED -> TaskAuditAction.TASK_BLOCKED;
                case DONE -> TaskAuditAction.TASK_COMPLETED;
                case CANCELLED -> TaskAuditAction.TASK_CANCELLED;
                default -> TaskAuditAction.TASK_STATUS_CHANGED;
            };
        }
        return onlyAssigneeChanged(before, after)
                ? TaskAuditAction.TASK_ASSIGNED
                : TaskAuditAction.TASK_METADATA_UPDATED;
    }

    private boolean onlyAssigneeChanged(TaskSnapshot before, TaskSnapshot after) {
        return !Objects.equals(before.assigneeId(), after.assigneeId())
                && Objects.equals(before.projectId(), after.projectId())
                && Objects.equals(before.groupId(), after.groupId())
                && Objects.equals(before.milestoneId(), after.milestoneId())
                && Objects.equals(before.parentTaskId(), after.parentTaskId())
                && Objects.equals(before.title(), after.title())
                && Objects.equals(before.description(), after.description())
                && Objects.equals(before.status(), after.status())
                && Objects.equals(before.priority(), after.priority())
                && Objects.equals(before.type(), after.type())
                && Objects.equals(before.dueDate(), after.dueDate())
                && Objects.equals(before.blockedReason(), after.blockedReason())
                && Objects.equals(before.progressPercent(), after.progressPercent());
    }

    private void record(
            TaskEntity task,
            User actor,
            TaskAuditAction action,
            TaskSnapshot before,
            TaskSnapshot after
    ) {
        Long taskId = Objects.requireNonNull(task.getId(), "task ID is required");
        Long userId = Objects.requireNonNull(actor, "actor is required").getId();
        if (userId == null) {
            throw new IllegalStateException("Activity actor must have a persisted ID");
        }
        taskActivityRepository.save(TaskActivityEntity.builder()
                .taskId(taskId)
                .userId(userId)
                .action(action)
                .oldValue(before == null ? null : serialize(before))
                .newValue(serialize(after))
                .build());
    }

    private String serialize(TaskSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize task activity snapshot", exception);
        }
    }

    public record TaskSnapshot(
            int schemaVersion,
            Long projectId,
            Long groupId,
            Long milestoneId,
            Long parentTaskId,
            Long assigneeId,
            String title,
            String description,
            TaskStatus status,
            TaskPriority priority,
            TaskType type,
            LocalDate dueDate,
            String blockedReason,
            Integer progressPercent
    ) {
    }
}
