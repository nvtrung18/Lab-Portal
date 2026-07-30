package com.web.labportalbackend.research.service.impl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.web.labportalbackend.admin.audit.enums.AuditAction;
import com.web.labportalbackend.admin.audit.enums.AuditModule;
import com.web.labportalbackend.admin.audit.service.AuditLogService;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.enums.UserStatus;
import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.dto.request.CreateTaskProposalRequest;
import com.web.labportalbackend.research.dto.request.RejectTaskProposalRequest;
import com.web.labportalbackend.research.dto.response.TaskProposalResponse;
import com.web.labportalbackend.research.dto.response.TaskProposalReviewResponse;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.GroupMemberEntity;
import com.web.labportalbackend.research.entity.MilestoneEntity;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.entity.TaskEntity;
import com.web.labportalbackend.research.entity.TaskProposalEntity;
import com.web.labportalbackend.research.enums.GroupRole;
import com.web.labportalbackend.research.enums.TaskPriority;
import com.web.labportalbackend.research.enums.TaskProposalStatus;
import com.web.labportalbackend.research.enums.TaskStatus;
import com.web.labportalbackend.research.enums.TaskType;
import com.web.labportalbackend.research.exception.TaskProposalReviewConflictException;
import com.web.labportalbackend.research.mapper.TaskMapper;
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
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

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

    @Override
    @Transactional
    public TaskProposalReviewResponse approve(Long proposalId) {
        ReviewContext context = loadAuthorizedReview(proposalId);
        ProposalPayload payload = deserializeAndValidatePayload(context);

        validateReviewMilestone(payload, context.project(), context.group());
        validateReviewParent(payload, context.project(), context.group());

        TaskEntity task = TaskEntity.builder()
                .projectId(context.project().getId())
                .groupId(context.group().getId())
                .milestoneId(payload.milestoneId())
                .parentTaskId(payload.parentTaskId())
                .assigneeId(null)
                .title(payload.title().trim())
                .description(payload.description())
                .deadline(payload.dueDate())
                .dueDate(payload.dueDate())
                .status(payload.milestoneId() == null ? TaskStatus.BACKLOG : TaskStatus.TODO)
                .priority(payload.priority())
                .type(payload.type())
                .createdBy(context.actor().getId())
                .progressPercent(0)
                .build();

        TaskEntity savedTask = taskRepository.saveAndFlush(task);
        Instant reviewedAt = Instant.now();
        TaskProposalEntity proposal = context.proposal();
        proposal.setStatus(TaskProposalStatus.APPROVED);
        proposal.setReviewedById(context.actor().getId());
        proposal.setReviewedAt(reviewedAt);
        proposal.setReason(null);
        taskProposalRepository.saveAndFlush(proposal);

        auditLogService.log(
                context.actor(),
                AuditAction.REVIEW_TASK_PROPOSAL,
                AuditModule.RESEARCH,
                "TASK_PROPOSAL",
                proposal.getId(),
                "Approved task proposal",
                serializeAuditMetadata(new ReviewAuditMetadata(
                        TaskProposalStatus.APPROVED,
                        savedTask.getId()
                ))
        );

        return reviewResponse(proposal, TaskMapper.toResponse(savedTask));
    }

    @Override
    @Transactional
    public TaskProposalReviewResponse reject(
            Long proposalId,
            RejectTaskProposalRequest request
    ) {
        String reason = normalizeRejectionReason(request);
        ReviewContext context = loadAuthorizedReview(proposalId);

        Instant reviewedAt = Instant.now();
        TaskProposalEntity proposal = context.proposal();
        proposal.setStatus(TaskProposalStatus.REJECTED);
        proposal.setReviewedById(context.actor().getId());
        proposal.setReviewedAt(reviewedAt);
        proposal.setReason(reason);
        taskProposalRepository.saveAndFlush(proposal);

        auditLogService.log(
                context.actor(),
                AuditAction.REVIEW_TASK_PROPOSAL,
                AuditModule.RESEARCH,
                "TASK_PROPOSAL",
                proposal.getId(),
                "Rejected task proposal",
                serializeAuditMetadata(new ReviewAuditMetadata(
                        TaskProposalStatus.REJECTED,
                        null
                ))
        );

        return reviewResponse(proposal, null);
    }

    private ReviewContext loadAuthorizedReview(Long proposalId) {
        if (proposalId == null) {
            throw new IllegalArgumentException("Task proposal ID is required");
        }

        Long actorId = resolveInitialActorId();

        // PR-001: retain only the scalar identity and clear stale state before
        // acquiring the proposal write lock. There must be no clear after this.
        entityManager.clear();
        TaskProposalEntity proposal = taskProposalRepository.findByIdForReview(proposalId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Task proposal", proposalId));

        User actor = userRepository.findByIdForStatusAuthorization(actorId)
                .filter(this::isUsableActor)
                .orElseThrow(() -> new AccessDeniedException(
                        "Authenticated user is inactive, deleted, or unavailable"));
        ProjectEntity project = projectRepository
                .findByIdForStatusAuthorization(proposal.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Project", proposal.getProjectId()));
        GroupEntity group = groupRepository
                .findByIdForStatusAuthorization(proposal.getGroupId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Research group", proposal.getGroupId()));
        Long laboratoryId = validateReviewProjectGroupScope(project, group);
        Laboratory laboratory = laboratoryRepository
                .findByIdForStatusAuthorization(laboratoryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Laboratory", laboratoryId));

        boolean authorized = false;
        if (actor.hasRole("LAB_MANAGER")) {
            authorized = laboratoryRepository
                    .findManagedByIdForStatusAuthorization(laboratory.getId(), actor.getId())
                    .isPresent();
        }
        if (!authorized && actor.hasRole("STUDENT")) {
            authorized = groupMemberRepository
                    .findActiveForStatusAuthorization(group.getId(), actor.getId())
                    .map(GroupMemberEntity::getRole)
                    .filter(GroupRole.LEADER::equals)
                    .isPresent();
        }
        if (!authorized) {
            throw new AccessDeniedException(
                    "Only the group leader or scoped laboratory manager may review this proposal");
        }

        if (proposal.getStatus() != TaskProposalStatus.PENDING) {
            throw new TaskProposalReviewConflictException(
                    "Task proposal has already reached a terminal review state");
        }
        return new ReviewContext(proposal, actor, project, group);
    }

    private Long validateReviewProjectGroupScope(
            ProjectEntity project,
            GroupEntity group
    ) {
        boolean currentModelMatch = group.getProject() != null
                && Objects.equals(project.getId(), group.getProject().getId());
        boolean legacyModelMatch = project.getGroup() != null
                && Objects.equals(group.getId(), project.getGroup().getId());
        boolean conflictingCurrentModel = group.getProject() != null
                && !currentModelMatch;
        boolean conflictingLegacyModel = project.getGroup() != null
                && !legacyModelMatch;
        if (conflictingCurrentModel
                || conflictingLegacyModel
                || (!currentModelMatch && !legacyModelMatch)) {
            throw new TaskProposalIntegrityException(
                    "Task proposal scope is inconsistent with current project and group state");
        }

        Long projectLabId = project.getLab() == null
                ? null
                : project.getLab().getId();
        Long groupLabId = group.getLab() == null
                ? null
                : group.getLab().getId();
        if (projectLabId == null
                || groupLabId == null
                || !projectLabId.equals(groupLabId)) {
            throw new TaskProposalIntegrityException(
                    "Task proposal project and group do not share a current laboratory");
        }
        return projectLabId;
    }

    private ProposalPayload deserializeAndValidatePayload(ReviewContext context) {
        ProposalPayload payload;
        try {
            JsonNode root = objectMapper.readerFor(JsonNode.class)
                    .with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
                    .readValue(context.proposal().getPayloadJson());
            validatePayloadJsonShape(root);
            ObjectReader strictReader = objectMapper.readerFor(ProposalPayload.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            payload = strictReader.readValue(root);
        } catch (Exception ex) {
            if (ex instanceof TaskProposalIntegrityException integrityException) {
                throw integrityException;
            }
            throw new TaskProposalIntegrityException(
                    "Stored task proposal payload cannot be reviewed", ex);
        }

        if (payload == null
                || payload.projectId() == null
                || payload.groupId() == null
                || payload.title() == null
                || payload.title().isBlank()
                || payload.title().trim().length() > 200
                || (payload.description() != null && payload.description().length() > 4000)
                || payload.priority() == null
                || payload.type() == null
                || !Objects.equals(payload.projectId(), context.proposal().getProjectId())
                || !Objects.equals(payload.groupId(), context.proposal().getGroupId())
                || !Objects.equals(payload.milestoneId(), context.proposal().getMilestoneId())
                || !Objects.equals(payload.projectId(), context.project().getId())
                || !Objects.equals(payload.groupId(), context.group().getId())) {
            throw new TaskProposalIntegrityException(
                    "Stored task proposal payload is incomplete or inconsistent");
        }
        return payload;
    }

    private void validatePayloadJsonShape(JsonNode root) {
        Set<String> expectedFields = Set.of(
                "projectId",
                "groupId",
                "milestoneId",
                "parentTaskId",
                "title",
                "description",
                "priority",
                "type",
                "dueDate"
        );
        if (root == null
                || !root.isObject()
                || root.size() != expectedFields.size()
                || !expectedFields.stream().allMatch(root::has)
                || !isLongNode(root.get("projectId"))
                || !isLongNode(root.get("groupId"))
                || !isNullableLongNode(root.get("milestoneId"))
                || !isNullableLongNode(root.get("parentTaskId"))
                || !root.get("title").isTextual()
                || !isNullableTextNode(root.get("description"))
                || !root.get("priority").isTextual()
                || !root.get("type").isTextual()
                || !isNullableTextNode(root.get("dueDate"))) {
            throw new TaskProposalIntegrityException(
                    "Stored task proposal payload has an invalid shape");
        }
    }

    private boolean isLongNode(JsonNode node) {
        return node != null && node.isIntegralNumber() && node.canConvertToLong();
    }

    private boolean isNullableLongNode(JsonNode node) {
        return node != null && (node.isNull() || isLongNode(node));
    }

    private boolean isNullableTextNode(JsonNode node) {
        return node != null && (node.isNull() || node.isTextual());
    }

    private void validateReviewMilestone(
            ProposalPayload payload,
            ProjectEntity project,
            GroupEntity group
    ) {
        try {
            validateMilestone(payload, project, group);
        } catch (IllegalArgumentException ex) {
            throw new TaskProposalIntegrityException(
                    "Stored task proposal milestone scope is inconsistent", ex);
        }
    }

    private void validateReviewParent(
            ProposalPayload payload,
            ProjectEntity project,
            GroupEntity group
    ) {
        try {
            validateParent(payload, project, group);
        } catch (IllegalArgumentException ex) {
            throw new TaskProposalIntegrityException(
                    "Stored task proposal parent scope is inconsistent", ex);
        }
    }

    private String normalizeRejectionReason(RejectTaskProposalRequest request) {
        if (request == null || request.getReason() == null) {
            throw new IllegalArgumentException("Rejection reason is required");
        }
        String rawReason = request.getReason();
        if (rawReason.length() > 4000 || containsDisallowedControlCharacter(rawReason)) {
            throw new IllegalArgumentException("Rejection reason is invalid");
        }
        String normalized = rawReason.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Rejection reason is required");
        }
        return normalized;
    }

    private boolean containsDisallowedControlCharacter(String value) {
        return value.codePoints().anyMatch(codePoint ->
                Character.isISOControl(codePoint)
                        && codePoint != '\t'
                        && codePoint != '\n'
                        && codePoint != '\r');
    }

    private String serializeAuditMetadata(ReviewAuditMetadata metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            throw new TaskProposalSerializationException(
                    "Task proposal review audit serialization failed", ex);
        }
    }

    private TaskProposalReviewResponse reviewResponse(
            TaskProposalEntity proposal,
            com.web.labportalbackend.research.dto.response.TaskResponse createdTask
    ) {
        return TaskProposalReviewResponse.builder()
                .proposalId(proposal.getId())
                .status(proposal.getStatus())
                .reviewedById(proposal.getReviewedById())
                .reason(proposal.getReason())
                .reviewedAt(proposal.getReviewedAt())
                .createdTask(createdTask)
                .build();
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

    private record ReviewContext(
            TaskProposalEntity proposal,
            User actor,
            ProjectEntity project,
            GroupEntity group
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ReviewAuditMetadata(
            TaskProposalStatus decision,
            Long createdTaskId
    ) {
    }

    private static final class TaskProposalSerializationException extends RuntimeException {
        private TaskProposalSerializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final class TaskProposalIntegrityException extends RuntimeException {
        private TaskProposalIntegrityException(String message) {
            super(message);
        }

        private TaskProposalIntegrityException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
