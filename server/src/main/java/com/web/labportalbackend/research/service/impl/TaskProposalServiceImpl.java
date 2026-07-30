package com.web.labportalbackend.research.service.impl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.labportalbackend.admin.audit.enums.AuditAction;
import com.web.labportalbackend.admin.audit.enums.AuditModule;
import com.web.labportalbackend.admin.audit.service.AuditLogService;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.enums.UserStatus;
import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.dto.request.CreateTaskProposalRequest;
import com.web.labportalbackend.research.dto.response.TaskProposalResponse;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.GroupMemberEntity;
import com.web.labportalbackend.research.entity.MilestoneEntity;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.entity.TaskEntity;
import com.web.labportalbackend.research.entity.TaskProposalEntity;
import com.web.labportalbackend.research.enums.GroupRole;
import com.web.labportalbackend.research.enums.TaskPriority;
import com.web.labportalbackend.research.enums.TaskProposalStatus;
import com.web.labportalbackend.research.enums.TaskType;
import com.web.labportalbackend.research.repository.GroupMemberRepository;
import com.web.labportalbackend.research.repository.GroupRepository;
import com.web.labportalbackend.research.repository.MilestoneRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.repository.TaskProposalRepository;
import com.web.labportalbackend.research.repository.TaskRepository;
import com.web.labportalbackend.research.service.TaskProposalService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TaskProposalServiceImpl implements TaskProposalService {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final GroupRepository groupRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final MilestoneRepository milestoneRepository;
    private final TaskRepository taskRepository;
    private final TaskProposalRepository taskProposalRepository;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public TaskProposalResponse submit(CreateTaskProposalRequest request) {
        Long actorId = resolveInitialActorId();
        ProposalPayload payload = normalize(request);
        String payloadJson = serialize(payload);
        entityManager.clear();

        // Fixed final authorization-lock order:
        // actor -> project -> group -> shared laboratory -> membership -> milestone -> parent.
        User actor = userRepository.findByIdForStatusAuthorization(actorId)
                .filter(this::isUsableActor)
                .orElseThrow(() -> new AccessDeniedException(
                        "Authenticated user is inactive, deleted, or unavailable"));
        if (!actor.hasRole("STUDENT") || actor.getRoles().size() != 1) {
            throw new AccessDeniedException("Only students can submit task proposals");
        }

        ProjectEntity project = projectRepository.findByIdForStatusAuthorization(payload.projectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project", payload.projectId()));
        GroupEntity group = groupRepository.findByIdForStatusAuthorization(payload.groupId())
                .orElseThrow(() -> new ResourceNotFoundException("Research group", payload.groupId()));

        Long sharedLabId = validateProjectGroupScope(project, group);
        laboratoryRepository.findByIdForStatusAuthorization(sharedLabId)
                .orElseThrow(() -> new ResourceNotFoundException("Laboratory", sharedLabId));

        GroupMemberEntity membership = groupMemberRepository
                .findActiveForStatusAuthorization(group.getId(), actor.getId())
                .orElseThrow(() -> new AccessDeniedException(
                        "Active membership in the requested group is required"));
        if (membership.getRole() != GroupRole.MEMBER && membership.getRole() != GroupRole.LEADER) {
            throw new AccessDeniedException("Membership role cannot submit task proposals");
        }

        validateMilestone(payload, project, group);
        validateParent(payload, project, group);

        TaskProposalEntity proposal = TaskProposalEntity.builder()
                .proposedById(actor.getId())
                .reviewedById(null)
                .projectId(project.getId())
                .groupId(group.getId())
                .milestoneId(payload.milestoneId())
                .aiActionSuggestionId(null)
                .assistedByAi(false)
                .payloadJson(payloadJson)
                .status(TaskProposalStatus.PENDING)
                .reason(null)
                .reviewedAt(null)
                .build();

        TaskProposalEntity saved = taskProposalRepository.saveAndFlush(proposal);
        auditLogService.log(
                actor,
                AuditAction.CREATE_TASK_PROPOSAL,
                AuditModule.RESEARCH,
                "TASK_PROPOSAL",
                saved.getId(),
                "Submitted task proposal"
        );
        return toResponse(saved, payload);
    }

    private Long resolveInitialActorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getName() == null
                || authentication.getName().isBlank()
                || "anonymousUser".equals(authentication.getName())) {
            throw new AccessDeniedException("Authentication is required");
        }
        return userRepository.findByUsername(authentication.getName())
                .map(User::getId)
                .orElseThrow(() -> new AccessDeniedException("Authenticated user was not found"));
    }

    private ProposalPayload normalize(CreateTaskProposalRequest request) {
        return new ProposalPayload(
                request.getProjectId(),
                request.getGroupId(),
                request.getMilestoneId(),
                request.getParentTaskId(),
                request.getTitle().trim(),
                request.getDescription(),
                request.getPriority() == null ? TaskPriority.MEDIUM : request.getPriority(),
                request.getType() == null ? TaskType.TASK : request.getType(),
                request.getDueDate()
        );
    }

    private String serialize(ProposalPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new TaskProposalSerializationException("Task proposal payload serialization failed", ex);
        }
    }

    private boolean isUsableActor(User actor) {
        return Boolean.TRUE.equals(actor.getActive())
                && !Boolean.TRUE.equals(actor.getDeleted())
                && actor.getStatus() == UserStatus.ACTIVE;
    }

    private Long validateProjectGroupScope(ProjectEntity project, GroupEntity group) {
        boolean currentModelMatch = group.getProject() != null
                && Objects.equals(project.getId(), group.getProject().getId());
        boolean legacyModelMatch = project.getGroup() != null
                && Objects.equals(group.getId(), project.getGroup().getId());
        boolean conflictingCurrentProject = group.getProject() != null && !currentModelMatch;
        if (conflictingCurrentProject || (!currentModelMatch && !legacyModelMatch)) {
            throw new IllegalArgumentException(
                    "Research group does not belong to the requested project");
        }

        Long projectLabId = project.getLab() == null ? null : project.getLab().getId();
        Long groupLabId = group.getLab() == null ? null : group.getLab().getId();
        if (projectLabId == null || groupLabId == null || !projectLabId.equals(groupLabId)) {
            throw new IllegalArgumentException(
                    "Project and research group must share an active laboratory");
        }
        return projectLabId;
    }

    private void validateMilestone(
            ProposalPayload payload,
            ProjectEntity project,
            GroupEntity group
    ) {
        if (payload.milestoneId() == null) {
            return;
        }
        MilestoneEntity milestone = milestoneRepository
                .findByIdForProposalSubmission(payload.milestoneId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Milestone", payload.milestoneId()));
        if (milestone.getProject() == null
                || !Objects.equals(project.getId(), milestone.getProject().getId())) {
            throw new IllegalArgumentException(
                    "Milestone does not belong to the requested project");
        }
        Long milestoneGroupId = milestone.getGroup() == null
                ? null
                : milestone.getGroup().getId();
        if (milestoneGroupId != null && !Objects.equals(group.getId(), milestoneGroupId)) {
            throw new IllegalArgumentException(
                    "Milestone group does not match the requested group");
        }
    }

    private void validateParent(
            ProposalPayload payload,
            ProjectEntity project,
            GroupEntity group
    ) {
        if (payload.parentTaskId() == null) {
            return;
        }
        TaskEntity parent = taskRepository
                .findByIdForProposalSubmission(payload.parentTaskId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Task", payload.parentTaskId()));
        if (!Objects.equals(project.getId(), parent.getProjectId())
                || !Objects.equals(group.getId(), parent.getGroupId())) {
            throw new IllegalArgumentException(
                    "Parent task must have the same project and group scope");
        }
    }

    private TaskProposalResponse toResponse(
            TaskProposalEntity entity,
            ProposalPayload payload
    ) {
        return TaskProposalResponse.builder()
                .id(entity.getId())
                .proposedById(entity.getProposedById())
                .projectId(entity.getProjectId())
                .groupId(entity.getGroupId())
                .milestoneId(entity.getMilestoneId())
                .parentTaskId(payload.parentTaskId())
                .title(payload.title())
                .description(payload.description())
                .priority(payload.priority())
                .type(payload.type())
                .dueDate(payload.dueDate())
                .assistedByAi(entity.getAssistedByAi())
                .aiActionSuggestionId(entity.getAiActionSuggestionId())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    private record ProposalPayload(
            Long projectId,
            Long groupId,
            Long milestoneId,
            Long parentTaskId,
            String title,
            String description,
            TaskPriority priority,
            TaskType type,
            LocalDate dueDate
    ) {
    }

    private static final class TaskProposalSerializationException extends RuntimeException {
        private TaskProposalSerializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
