package com.web.labportalbackend.research.service.impl;

import com.web.labportalbackend.admin.audit.enums.AuditAction;
import com.web.labportalbackend.admin.audit.enums.AuditModule;
import com.web.labportalbackend.admin.audit.service.AuditLogService;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.dto.request.PatchResearchTaskRequest;
import com.web.labportalbackend.research.dto.response.TaskResponse;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.MilestoneEntity;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.entity.TaskEntity;
import com.web.labportalbackend.research.enums.GroupRole;
import com.web.labportalbackend.research.enums.TaskPriority;
import com.web.labportalbackend.research.enums.TaskStatus;
import com.web.labportalbackend.research.enums.TaskType;
import com.web.labportalbackend.research.mapper.TaskMapper;
import com.web.labportalbackend.research.repository.GroupMemberRepository;
import com.web.labportalbackend.research.repository.GroupRepository;
import com.web.labportalbackend.research.repository.MilestoneRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class TaskMetadataPatchService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final GroupRepository groupRepository;
    private final MilestoneRepository milestoneRepository;
    private final UserRepository userRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final AuditLogService auditLogService;
    private final TaskActivityRecorder taskActivityRecorder;

    public TaskResponse patch(Long taskId, PatchResearchTaskRequest request) {
        validateRequestShape(request);
        validateRequiredPatchValues(request);

        User actor = getUsableCurrentUser();
        ActorType actorType = resolveActorType(actor);
        TaskEntity current = taskRepository.findByIdAndDeletedFalseAndActiveTrue(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
        authorize(actor, actorType, current);
        if (actorType == ActorType.LEADER && request.isGroupIdPresent()) {
            throw new AccessDeniedException("Leaders cannot change or resend task groupId");
        }

        TaskEntity locked = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
        assertUsableTask(locked);
        assertScopeUnchanged(current, locked);
        ProjectEntity project = loadProject(locked.getProjectId());
        authorize(actor, actorType, locked);

        Candidate candidate = candidateFrom(locked, request);
        ResolvedCandidate resolved = validateCandidate(locked, candidate, project, actorType);
        List<String> changedFields = changedFields(locked, candidate, request);
        if (changedFields.isEmpty()) {
            return TaskMapper.toResponse(locked);
        }

        TaskActivityRecorder.TaskSnapshot before = taskActivityRecorder.capture(locked);
        apply(locked, candidate, resolved, request);
        TaskEntity saved = taskRepository.save(locked);
        taskActivityRecorder.recordMutation(before, saved, actor);
        auditLogService.log(
                actor,
                AuditAction.UPDATE_RESEARCH_TASK,
                AuditModule.RESEARCH,
                "RESEARCH_TASK",
                saved.getId(),
                "Updated research task metadata",
                changedFieldsMetadata(changedFields)
        );
        return TaskMapper.toResponse(saved);
    }

    private void validateRequestShape(PatchResearchTaskRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Task patch request is required");
        }
        if (!request.getUnknownFields().isEmpty()) {
            throw new IllegalArgumentException("Unknown task patch fields: " + request.getUnknownFields());
        }
        if (!request.hasAnyRecognizedField()) {
            throw new IllegalArgumentException("Task patch request must contain at least one recognized field");
        }
    }

    private void validateRequiredPatchValues(PatchResearchTaskRequest request) {
        if (request.isTitlePresent()) {
            if (request.getTitle() == null) {
                throw new IllegalArgumentException("Title cannot be null");
            }
            if (request.getTitle().trim().isEmpty()) {
                throw new IllegalArgumentException("Title cannot be blank");
            }
        }
        if (request.isPriorityPresent() && request.getPriority() == null) {
            throw new IllegalArgumentException("Priority cannot be null");
        }
        if (request.isTypePresent() && request.getType() == null) {
            throw new IllegalArgumentException("Type cannot be null");
        }
    }

    private User getUsableCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new AccessDeniedException("Authentication is required");
        }
        return userRepository.findByUsername(authentication.getName())
                .filter(user -> Boolean.TRUE.equals(user.getActive()))
                .filter(user -> !Boolean.TRUE.equals(user.getDeleted()))
                .orElseThrow(() -> new AccessDeniedException("Authenticated user is inactive or deleted"));
    }

    private ActorType resolveActorType(User actor) {
        if (actor.hasRole("LAB_MANAGER")) {
            return ActorType.MANAGER;
        }
        if (actor.hasRole("STUDENT")) {
            return ActorType.LEADER;
        }
        throw new AccessDeniedException("Current role cannot patch research task metadata");
    }

    private void authorize(User actor, ActorType actorType, TaskEntity task) {
        assertUsableTask(task);
        if (actorType == ActorType.MANAGER) {
            ProjectEntity project = loadProject(task.getProjectId());
            if (project.getLab() == null || project.getLab().getId() == null
                    || !laboratoryRepository.existsByIdAndManagerIdAndActiveTrueAndDeletedFalse(
                    project.getLab().getId(), actor.getId())) {
                throw new AccessDeniedException("Cannot update task metadata outside the managed lab");
            }
            return;
        }
        if (task.getGroupId() == null || groupMemberRepository.findActiveRoleByGroupIdAndUserId(
                task.getGroupId(), actor.getId()).orElse(null) != GroupRole.LEADER) {
            throw new AccessDeniedException("Only the active leader of the current task group can update metadata");
        }
    }

    private void assertUsableTask(TaskEntity task) {
        if (task == null || task.getId() == null || !Boolean.TRUE.equals(task.getActive())
                || Boolean.TRUE.equals(task.getDeleted()) || task.getProjectId() == null) {
            throw new AccessDeniedException("Task is not active in a valid project scope");
        }
    }

    private void assertScopeUnchanged(TaskEntity current, TaskEntity locked) {
        if (!Objects.equals(current.getProjectId(), locked.getProjectId())
                || !Objects.equals(current.getGroupId(), locked.getGroupId())) {
            throw new AccessDeniedException("Task scope changed while authorizing metadata update");
        }
    }

    private ProjectEntity loadProject(Long projectId) {
        return projectRepository.findByIdAndDeletedFalseAndActiveTrue(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
    }

    private Candidate candidateFrom(TaskEntity task, PatchResearchTaskRequest request) {
        return new Candidate(
                request.isGroupIdPresent() ? request.getGroupId() : task.getGroupId(),
                request.isMilestoneIdPresent() ? request.getMilestoneId() : task.getMilestoneId(),
                request.isParentTaskIdPresent() ? request.getParentTaskId() : task.getParentTaskId(),
                request.isAssigneeIdPresent() ? request.getAssigneeId() : task.getAssigneeId(),
                request.isTitlePresent() ? request.getTitle().trim() : task.getTitle(),
                request.isDescriptionPresent() ? request.getDescription() : task.getDescription(),
                request.isPriorityPresent() ? request.getPriority() : task.getPriority(),
                request.isTypePresent() ? request.getType() : task.getType(),
                request.isDueDatePresent() ? request.getDueDate() : task.getDueDate()
        );
    }

    private ResolvedCandidate validateCandidate(
            TaskEntity task,
            Candidate candidate,
            ProjectEntity project,
            ActorType actorType
    ) {
        if (candidate.title() == null || candidate.title().trim().isEmpty()) {
            throw new IllegalArgumentException("Final task title cannot be blank");
        }
        if (candidate.priority() == null || candidate.type() == null) {
            throw new IllegalArgumentException("Final task priority and type are required");
        }

        GroupEntity group = null;
        if (candidate.groupId() != null) {
            group = groupRepository.findByIdAndDeletedFalseAndActiveTrue(candidate.groupId())
                    .orElseThrow(() -> new ResourceNotFoundException("Research group", candidate.groupId()));
            validateGroupScope(group, project);
        }

        MilestoneEntity milestone = null;
        if (candidate.milestoneId() != null) {
            milestone = milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(candidate.milestoneId())
                    .orElseThrow(() -> new ResourceNotFoundException("Milestone", candidate.milestoneId()));
            if (milestone.getProject() == null || !Objects.equals(project.getId(), milestone.getProject().getId())) {
                throw new IllegalArgumentException("Milestone does not belong to the task project");
            }
            Long milestoneGroupId = milestone.getGroup() == null ? null : milestone.getGroup().getId();
            if (milestoneGroupId != null && !Objects.equals(milestoneGroupId, candidate.groupId())) {
                throw new IllegalArgumentException("Milestone group does not match the final task group");
            }
        }

        TaskEntity parent = resolveAndValidateParent(task, candidate, project);
        User assignee = resolveAndValidateAssignee(candidate);

        boolean clearingGroup = task.getGroupId() != null && candidate.groupId() == null;
        if (clearingGroup) {
            if (actorType != ActorType.MANAGER) {
                throw new AccessDeniedException("Only a lab manager can clear a task group");
            }
            if (task.getStatus() != TaskStatus.BACKLOG) {
                throw new IllegalArgumentException("Only BACKLOG tasks can be moved to project level");
            }
            if (candidate.assigneeId() != null) {
                throw new IllegalArgumentException("A project-level task cannot retain an assignee");
            }
            if (parent != null && parent.getGroupId() != null) {
                throw new IllegalArgumentException("A project-level task cannot retain a group-level parent");
            }
            if (milestone != null && milestone.getGroup() != null) {
                throw new IllegalArgumentException("A project-level task cannot retain a group-level milestone");
            }
        }
        return new ResolvedCandidate(assignee);
    }

    private TaskEntity resolveAndValidateParent(TaskEntity task, Candidate candidate, ProjectEntity project) {
        if (candidate.parentTaskId() == null) {
            return null;
        }
        if (Objects.equals(task.getId(), candidate.parentTaskId())) {
            throw new IllegalArgumentException("A task cannot be its own parent");
        }

        boolean parentChanged = !Objects.equals(task.getParentTaskId(), candidate.parentTaskId());
        if (!parentChanged) {
            TaskEntity parent = taskRepository.findByIdAndDeletedFalseAndActiveTrue(candidate.parentTaskId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent task", candidate.parentTaskId()));
            validateParentScope(parent, project.getId(), candidate.groupId());
            return parent;
        }

        Set<Long> visited = new LinkedHashSet<>();
        Long ancestorId = candidate.parentTaskId();
        TaskEntity proposedParent = null;
        while (ancestorId != null) {
            if (Objects.equals(task.getId(), ancestorId)) {
                throw new IllegalArgumentException("Task parent hierarchy would contain a cycle");
            }
            if (!visited.add(ancestorId)) {
                throw new IllegalArgumentException("Existing task parent hierarchy contains a cycle");
            }
            Long lookupId = ancestorId;
            TaskEntity ancestor = taskRepository.findByIdForUpdate(lookupId)
                    .orElseThrow(() -> new ResourceNotFoundException("Parent task", lookupId));
            validateParentScope(ancestor, project.getId(), candidate.groupId());
            if (proposedParent == null) {
                proposedParent = ancestor;
            }
            ancestorId = ancestor.getParentTaskId();
        }
        return proposedParent;
    }

    private void validateParentScope(TaskEntity parent, Long projectId, Long groupId) {
        if (!Objects.equals(projectId, parent.getProjectId()) || !Objects.equals(groupId, parent.getGroupId())) {
            throw new IllegalArgumentException("Parent task must have the same project and group scope");
        }
    }

    private User resolveAndValidateAssignee(Candidate candidate) {
        if (candidate.assigneeId() == null) {
            return null;
        }
        if (candidate.groupId() == null) {
            throw new IllegalArgumentException("An assignee requires a research group");
        }
        User assignee = userRepository.findById(candidate.assigneeId())
                .filter(user -> Boolean.TRUE.equals(user.getActive()))
                .filter(user -> !Boolean.TRUE.equals(user.getDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("Assignee", candidate.assigneeId()));
        if (!groupMemberRepository.existsByGroupIdAndUserIdAndActiveTrueAndDeletedFalse(
                candidate.groupId(), candidate.assigneeId())) {
            throw new IllegalArgumentException("Assignee is not an active member of the final task group");
        }
        return assignee;
    }

    private void validateGroupScope(GroupEntity group, ProjectEntity project) {
        boolean newModelMatch = group.getProject() != null && project.getId().equals(group.getProject().getId());
        boolean legacyModelMatch = project.getGroup() != null && group.getId().equals(project.getGroup().getId());
        boolean sameLab = group.getLab() != null && group.getLab().getId() != null
                && project.getLab() != null && group.getLab().getId().equals(project.getLab().getId());
        if ((group.getProject() != null && !newModelMatch) || (!newModelMatch && !legacyModelMatch) || !sameLab) {
            throw new IllegalArgumentException("Research group does not belong to the task project and lab");
        }
    }

    private List<String> changedFields(
            TaskEntity task,
            Candidate candidate,
            PatchResearchTaskRequest request
    ) {
        List<String> changed = new ArrayList<>();
        addChanged(changed, "groupId", task.getGroupId(), candidate.groupId());
        addChanged(changed, "milestoneId", task.getMilestoneId(), candidate.milestoneId());
        addChanged(changed, "parentTaskId", task.getParentTaskId(), candidate.parentTaskId());
        addChanged(changed, "title", task.getTitle(), candidate.title());
        addChanged(changed, "description", task.getDescription(), candidate.description());
        addChanged(changed, "assigneeId", task.getAssigneeId(), candidate.assigneeId());
        addChanged(changed, "priority", task.getPriority(), candidate.priority());
        addChanged(changed, "type", task.getType(), candidate.type());
        if (request.isDueDatePresent()
                && (!Objects.equals(task.getDueDate(), candidate.dueDate())
                || !Objects.equals(task.getDeadline(), candidate.dueDate()))) {
            changed.add("dueDate");
        }
        return changed;
    }

    private void addChanged(List<String> changed, String field, Object currentValue, Object finalValue) {
        if (!Objects.equals(currentValue, finalValue)) {
            changed.add(field);
        }
    }

    private void apply(
            TaskEntity task,
            Candidate candidate,
            ResolvedCandidate resolved,
            PatchResearchTaskRequest request
    ) {
        task.setGroupId(candidate.groupId());
        task.setMilestoneId(candidate.milestoneId());
        task.setParentTaskId(candidate.parentTaskId());
        task.setTitle(candidate.title());
        task.setDescription(candidate.description());
        task.setAssigneeId(candidate.assigneeId());
        if (request.isAssigneeIdPresent()) {
            task.setAssignedToStudent(resolved.assignee());
        }
        task.setPriority(candidate.priority());
        task.setType(candidate.type());
        if (request.isDueDatePresent()) {
            task.setDueDate(candidate.dueDate());
            task.setDeadline(candidate.dueDate());
        }
    }

    private String changedFieldsMetadata(List<String> changedFields) {
        return "{\"changedFields\":[\"" + String.join("\",\"", changedFields) + "\"]}";
    }

    private enum ActorType {
        MANAGER,
        LEADER
    }

    private record Candidate(
            Long groupId,
            Long milestoneId,
            Long parentTaskId,
            Long assigneeId,
            String title,
            String description,
            TaskPriority priority,
            TaskType type,
            java.time.LocalDate dueDate
    ) {
    }

    private record ResolvedCandidate(User assignee) {
    }
}
