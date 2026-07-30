package com.web.labportalbackend.research.service.impl;

import com.web.labportalbackend.admin.audit.enums.AuditAction;
import com.web.labportalbackend.admin.audit.enums.AuditModule;
import com.web.labportalbackend.admin.audit.service.AuditLogService;
import com.web.labportalbackend.admin.systemconfig.dto.SystemConfigResponse;
import com.web.labportalbackend.admin.systemconfig.service.SystemConfigService;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.research.dto.request.PatchTaskStatusRequest;
import com.web.labportalbackend.research.dto.response.TaskResponse;
import com.web.labportalbackend.research.entity.TaskEntity;
import com.web.labportalbackend.research.enums.GroupRole;
import com.web.labportalbackend.research.enums.TaskStatus;
import com.web.labportalbackend.research.mapper.TaskMapper;
import com.web.labportalbackend.research.repository.ReportRepository;
import com.web.labportalbackend.research.repository.TaskRepository;
import com.web.labportalbackend.research.security.TaskPermissionHelper;
import com.web.labportalbackend.research.service.TaskWorkflowService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Canonical status use case. The outer transaction belongs to TaskServiceImpl;
 * this component deliberately has no independent transaction boundary.
 */
@Component
@RequiredArgsConstructor
public class TaskStatusUpdateService {

    private static final int MAX_BLOCKED_REASON_LENGTH = 4000;

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final TaskPermissionHelper taskPermissionHelper;
    private final ReportRepository reportRepository;
    private final SystemConfigService systemConfigService;
    private final TaskWorkflowService taskWorkflowService;
    private final AuditLogService auditLogService;
    private final EntityManager entityManager;
    private final TaskActivityRecorder taskActivityRecorder;

    public TaskResponse patch(Long taskId, PatchTaskStatusRequest request) {
        validateRequestShape(request);

        User initialActor = getUsableCurrentUser();
        TaskEntity initialTask = taskRepository.findByIdAndDeletedFalseAndActiveTrue(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
        Capabilities preliminary = resolveCapabilities(initialActor, initialTask);
        assertAnyCapability(preliminary);

        ScopeSnapshot initialScope = ScopeSnapshot.from(initialTask);
        entityManager.clear();

        TaskEntity locked = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
        if (!initialScope.matches(locked)) {
            throw new AccessDeniedException("Task scope changed while authorizing status update");
        }

        User actor = userRepository.findByIdForStatusAuthorization(initialActor.getId())
                .filter(this::isUsable)
                .orElseThrow(() -> new AccessDeniedException("Authenticated user is inactive or deleted"));
        Capabilities finalCapabilities = resolveLockedCapabilities(actor, locked);
        assertAnyCapability(finalCapabilities);

        boolean requireApprovedReport;
        boolean hasApprovedReport = false;
        SystemConfigResponse config = systemConfigService.getConfigForStatusAuthorization();
        if (config == null || config.research() == null) {
            throw new IllegalStateException("System configuration is unavailable");
        }
        requireApprovedReport = config.research().requireApprovedReportBeforeTaskDone();
        if (request.getStatus() == TaskStatus.DONE && requireApprovedReport) {
            hasApprovedReport = !reportRepository
                    .findLatestApprovedForStatusAuthorization(locked.getId())
                    .isEmpty();
        }

        TaskWorkflowService.TaskTransitionContext context = new TaskWorkflowService.TaskTransitionContext(
                locked.getId(),
                actor.getId(),
                locked.getStatus(),
                request.getStatus(),
                locked.getProgressPercent(),
                request.getBlockedReason(),
                finalCapabilities.managerInScope(),
                finalCapabilities.leaderInScope(),
                finalCapabilities.assignee(),
                finalCapabilities.activeGroupMember(),
                hasApprovedReport,
                requireApprovedReport
        );
        TaskWorkflowService.TaskTransitionDecision decision = taskWorkflowService.evaluate(context);

        if (decision.statusUnchanged() && !decision.blockedReasonChanged()) {
            return TaskMapper.toResponse(locked);
        }

        TaskActivityRecorder.TaskSnapshot before = taskActivityRecorder.capture(locked);
        Integer oldProgress = locked.getProgressPercent();
        if (!decision.statusUnchanged()) {
            locked.setStatus(decision.toStatus());
        }
        if (decision.blockedReasonChanged()) {
            locked.setBlockedReason(decision.resolvedBlockedReason());
        }
        if (decision.resolvedProgressPercent() != null) {
            locked.setProgressPercent(decision.resolvedProgressPercent());
        }

        boolean progressChanged = decision.resolvedProgressPercent() != null
                && !Objects.equals(oldProgress, decision.resolvedProgressPercent());
        TaskEntity saved = taskRepository.save(locked);
        taskActivityRecorder.recordMutation(before, saved, actor);
        String metadata = "{"
                + "\"fromStatus\":\"" + decision.fromStatus().name() + "\","
                + "\"toStatus\":\"" + decision.toStatus().name() + "\","
                + "\"actorCapability\":\"" + decision.actor().name() + "\","
                + "\"blockedReasonChanged\":" + decision.blockedReasonChanged() + ","
                + "\"progressChanged\":" + progressChanged
                + "}";
        auditLogService.log(
                actor,
                AuditAction.UPDATE_RESEARCH_TASK_STATUS,
                AuditModule.RESEARCH,
                "RESEARCH_TASK",
                saved.getId(),
                "Updated research task status",
                metadata
        );
        return TaskMapper.toResponse(saved);
    }

    private void validateRequestShape(PatchTaskStatusRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Task status request is required");
        }
        if (!request.getUnknownFields().isEmpty()) {
            throw new IllegalArgumentException("Unknown task status fields: " + request.getUnknownFields());
        }
        if (request.getStatus() == null) {
            throw new IllegalArgumentException("Task status is required");
        }
        String reason = request.getBlockedReason();
        if (reason != null) {
            if (reason.length() > MAX_BLOCKED_REASON_LENGTH) {
                throw new IllegalArgumentException("Blocked reason must not exceed 4000 characters");
            }
            if (!hasAllowedControlCharacters(reason)) {
                throw new IllegalArgumentException("Blocked reason contains disallowed control characters");
            }
        }
    }

    private boolean hasAllowedControlCharacters(String value) {
        return value.codePoints().allMatch(cp ->
                Character.getType(cp) != Character.CONTROL
                        || cp == '\t' || cp == '\n' || cp == '\r');
    }

    private User getUsableCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null) {
            throw new AccessDeniedException("Authentication is required");
        }
        return userRepository.findByUsername(authentication.getName())
                .filter(this::isUsable)
                .orElseThrow(() -> new AccessDeniedException("Authenticated user is inactive or deleted"));
    }

    private boolean isUsable(User user) {
        return user != null
                && Boolean.TRUE.equals(user.getActive())
                && !Boolean.TRUE.equals(user.getDeleted());
    }

    private Capabilities resolveCapabilities(User actor, TaskEntity task) {
        boolean usable = isUsable(actor);
        boolean student = usable && actor.hasRole("STUDENT");
        boolean manager = usable && actor.hasRole("LAB_MANAGER")
                && taskPermissionHelper.isManagerOfTaskProjectOrLab(actor.getId(), task);
        boolean hasGroup = task != null && task.getGroupId() != null;
        boolean leader = student && hasGroup
                && taskPermissionHelper.isLeaderInTaskGroup(actor.getId(), task);
        boolean assignee = student && hasGroup
                && taskPermissionHelper.isTaskAssignee(actor.getId(), task);
        boolean activeMember = student && hasGroup
                && taskPermissionHelper.isMemberInTaskGroup(actor.getId(), task);
        return new Capabilities(manager, leader, assignee, activeMember);
    }

    private Capabilities resolveLockedCapabilities(User actor, TaskEntity task) {
        TaskPermissionHelper.StatusAuthorizationScope scope =
                taskPermissionHelper.resolveStatusAuthorizationScope(task);
        if (!scope.valid()) {
            return new Capabilities(false, false, false, false);
        }

        boolean usable = isUsable(actor);
        boolean student = usable && actor.hasRole("STUDENT");
        boolean manager = usable
                && actor.hasRole("LAB_MANAGER")
                && taskPermissionHelper.isLaboratoryManagedByForStatusAuthorization(
                        scope.laboratoryId(), actor.getId());
        GroupRole groupRole = student
                ? taskPermissionHelper.findGroupRoleForStatusAuthorization(actor.getId(), task)
                : null;
        boolean activeMember = groupRole == GroupRole.LEADER || groupRole == GroupRole.MEMBER;
        boolean leader = groupRole == GroupRole.LEADER;
        boolean assignee = student
                && task.getGroupId() != null
                && task.getAssigneeId() != null
                && actor.getId().equals(task.getAssigneeId());
        return new Capabilities(manager, leader, assignee, activeMember);
    }

    private void assertAnyCapability(Capabilities capabilities) {
        if (!capabilities.managerInScope()
                && !(capabilities.leaderInScope() && capabilities.activeGroupMember())
                && !(capabilities.assignee() && capabilities.activeGroupMember())) {
            throw new AccessDeniedException("Actor may not update this task status");
        }
    }

    private record Capabilities(
            boolean managerInScope,
            boolean leaderInScope,
            boolean assignee,
            boolean activeGroupMember
    ) {
    }

    private record ScopeSnapshot(Long projectId, Long groupId, Long assigneeId) {
        static ScopeSnapshot from(TaskEntity task) {
            return new ScopeSnapshot(task.getProjectId(), task.getGroupId(), task.getAssigneeId());
        }

        boolean matches(TaskEntity task) {
            return Objects.equals(projectId, task.getProjectId())
                    && Objects.equals(groupId, task.getGroupId())
                    && Objects.equals(assigneeId, task.getAssigneeId());
        }
    }
}
