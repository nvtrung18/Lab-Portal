package com.web.labportalbackend.ai.security.impl;

import com.web.labportalbackend.ai.enums.AiAssistantDomain;
import com.web.labportalbackend.ai.enums.AiCapabilityDenialReason;
import com.web.labportalbackend.ai.enums.AiCapabilityEvidence;
import com.web.labportalbackend.ai.enums.AiResourceScope;
import com.web.labportalbackend.ai.enums.AiResourceType;
import com.web.labportalbackend.ai.security.AiCapabilityPermissionAdapter;
import com.web.labportalbackend.ai.service.AiCapabilityDecision;
import com.web.labportalbackend.ai.service.AiCapabilityRequest;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.entity.ReportEntity;
import com.web.labportalbackend.research.entity.TaskEntity;
import com.web.labportalbackend.research.enums.GroupRole;
import com.web.labportalbackend.research.repository.GroupMemberRepository;
import com.web.labportalbackend.research.repository.GroupRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.repository.ReportRepository;
import com.web.labportalbackend.research.repository.TaskRepository;
import com.web.labportalbackend.research.security.TaskPermissionHelper;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class AiResearchCapabilityPermissionAdapter implements AiCapabilityPermissionAdapter {

    private final TaskPermissionHelper taskPermissionHelper;
    private final ProjectRepository projectRepository;
    private final GroupRepository groupRepository;
    private final TaskRepository taskRepository;
    private final ReportRepository reportRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final LaboratoryRepository laboratoryRepository;

    public AiResearchCapabilityPermissionAdapter(TaskPermissionHelper taskPermissionHelper,
                                                 ProjectRepository projectRepository,
                                                 GroupRepository groupRepository,
                                                 TaskRepository taskRepository,
                                                 ReportRepository reportRepository,
                                                 GroupMemberRepository groupMemberRepository,
                                                 LaboratoryRepository laboratoryRepository) {
        this.taskPermissionHelper = taskPermissionHelper;
        this.projectRepository = projectRepository;
        this.groupRepository = groupRepository;
        this.taskRepository = taskRepository;
        this.reportRepository = reportRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.laboratoryRepository = laboratoryRepository;
    }

    @Override
    public AiAssistantDomain domain() {
        return AiAssistantDomain.RESEARCH;
    }

    @Override
    public Evaluation evaluate(User actor, AiCapabilityRequest request) {
        try {
            return switch (request.capability()) {
                case RESEARCH_PROJECT_SUMMARY -> projectSummary(actor, request);
                case RESEARCH_GROUP_SUMMARY -> groupSummary(actor, request);
                case RESEARCH_ASSIGNED_TASK_READ -> assignedTask(actor, request);
                case RESEARCH_TASK_PROPOSAL_DRAFT -> proposalDraft(actor, request);
                case RESEARCH_TASK_SUGGESTION_DRAFT -> taskSuggestion(actor, request);
                case RESEARCH_REPORT_REVIEW_DRAFT -> reportReview(actor, request);
                default -> Evaluation.denied(AiCapabilityDenialReason.DOMAIN_MISMATCH);
            };
        } catch (RuntimeException ex) {
            return Evaluation.denied(AiCapabilityDenialReason.RESOURCE_UNAVAILABLE);
        }
    }

    private Evaluation projectSummary(User actor, AiCapabilityRequest request) {
        ProjectEntity project = activeProject(request.resource().id());
        if (project == null) {
            return unavailable();
        }
        if (!taskPermissionHelper.canViewProjectContext(actor.getId(), project)) {
            return Evaluation.denied(AiCapabilityDenialReason.RESOURCE_OUT_OF_SCOPE);
        }
        return allow(AiResourceType.PROJECT, project.getId(), project.getLab().getId(), project.getId(),
                null, null, AiResourceScope.EXISTING_BUSINESS_PERMISSION,
                AiCapabilityEvidence.EXISTING_PERMISSION);
    }

    private Evaluation groupSummary(User actor, AiCapabilityRequest request) {
        GroupEntity group = activeGroup(request.resource().id());
        if (group == null) {
            return unavailable();
        }
        Long labId = group.getLab().getId();
        if (actor.hasRole("STUDENT")) {
            GroupRole role = groupMemberRepository.findActiveRoleByGroupIdAndUserId(group.getId(), actor.getId())
                    .orElse(null);
            if (role != null) {
                AiResourceScope scope = role == GroupRole.LEADER
                        ? AiResourceScope.GROUP_LEADER : AiResourceScope.GROUP_MEMBER;
                return allow(AiResourceType.GROUP, group.getId(), labId, projectId(group), group.getId(), null,
                        scope, role == GroupRole.LEADER
                                ? AiCapabilityEvidence.GROUP_LEADERSHIP : AiCapabilityEvidence.GROUP_MEMBERSHIP);
            }
        }
        if (actor.hasRole("LAB_MANAGER")) {
            if (laboratoryRepository.existsByIdAndManagerIdAndActiveTrueAndDeletedFalse(labId, actor.getId())) {
                return allow(AiResourceType.GROUP, group.getId(), labId, projectId(group), group.getId(), null,
                        AiResourceScope.MANAGED_LAB, AiCapabilityEvidence.MANAGED_LAB);
            }
        }
        if (actor.hasRole("STUDENT")) {
            return Evaluation.denied(AiCapabilityDenialReason.NOT_GROUP_MEMBER);
        }
        if (actor.hasRole("LAB_MANAGER")) {
            return Evaluation.denied(AiCapabilityDenialReason.NOT_MANAGED_LAB);
        }
        return Evaluation.denied(AiCapabilityDenialReason.ROLE_NOT_ALLOWED);
    }

    private Evaluation assignedTask(User actor, AiCapabilityRequest request) {
        TaskEntity task = activeTask(request.resource().id());
        if (task == null) {
            return unavailable();
        }
        TaskScope scope = taskScope(task);
        if (scope == null) {
            return Evaluation.denied(AiCapabilityDenialReason.RESOURCE_OUT_OF_SCOPE);
        }
        if (!taskPermissionHelper.canViewTask(actor.getId(), scope.task())) {
            return Evaluation.denied(AiCapabilityDenialReason.RESOURCE_OUT_OF_SCOPE);
        }
        if (!taskPermissionHelper.isTaskAssignee(actor.getId(), scope.task())) {
            return Evaluation.denied(AiCapabilityDenialReason.NOT_ASSIGNED);
        }
        return allowTask(scope, AiResourceScope.ASSIGNED, AiCapabilityEvidence.ASSIGNMENT);
    }

    private Evaluation proposalDraft(User actor, AiCapabilityRequest request) {
        if (!actor.hasRole("STUDENT") || actor.getRoles() == null || actor.getRoles().size() != 1) {
            return Evaluation.denied(AiCapabilityDenialReason.ROLE_NOT_ALLOWED);
        }
        ProjectEntity project = activeProject(request.parentResource().id());
        GroupEntity group = activeGroup(request.resource().id());
        if (project == null || group == null) {
            return unavailable();
        }
        if (!coherent(project, group)) {
            return Evaluation.denied(AiCapabilityDenialReason.RESOURCE_OUT_OF_SCOPE);
        }
        GroupRole role = groupMemberRepository.findActiveRoleByGroupIdAndUserId(group.getId(), actor.getId())
                .orElse(null);
        if (role != GroupRole.MEMBER && role != GroupRole.LEADER) {
            return Evaluation.denied(AiCapabilityDenialReason.NOT_GROUP_MEMBER);
        }
        return allow(AiResourceType.GROUP, group.getId(), group.getLab().getId(), project.getId(), group.getId(),
                null, role == GroupRole.LEADER ? AiResourceScope.GROUP_LEADER : AiResourceScope.GROUP_MEMBER,
                role == GroupRole.LEADER
                        ? AiCapabilityEvidence.GROUP_LEADERSHIP : AiCapabilityEvidence.GROUP_MEMBERSHIP);
    }

    private Evaluation taskSuggestion(User actor, AiCapabilityRequest request) {
        TaskEntity task = activeTask(request.resource().id());
        if (task == null) {
            return unavailable();
        }
        TaskScope scope = taskScope(task);
        if (scope == null) {
            return Evaluation.denied(AiCapabilityDenialReason.RESOURCE_OUT_OF_SCOPE);
        }
        if (!taskPermissionHelper.canViewTask(actor.getId(), scope.task())) {
            return Evaluation.denied(AiCapabilityDenialReason.RESOURCE_OUT_OF_SCOPE);
        }
        if (taskPermissionHelper.isTaskAssignee(actor.getId(), scope.task())) {
            return allowTask(scope, AiResourceScope.ASSIGNED, AiCapabilityEvidence.ASSIGNMENT);
        }
        if (!taskPermissionHelper.canManageTask(actor.getId(), scope.task())) {
            return Evaluation.denied(AiCapabilityDenialReason.RESOURCE_OUT_OF_SCOPE);
        }
        return allowTask(scope, AiResourceScope.EXISTING_BUSINESS_PERMISSION,
                AiCapabilityEvidence.EXISTING_PERMISSION);
    }

    private Evaluation reportReview(User actor, AiCapabilityRequest request) {
        ReportEntity report = reportRepository.findById(request.resource().id())
                .filter(value -> Boolean.TRUE.equals(value.getActive()))
                .filter(value -> !Boolean.TRUE.equals(value.getDeleted()))
                .orElse(null);
        if (report == null || report.getProjectId() == null || report.getGroupId() == null
                || report.getMilestoneId() == null) {
            return unavailable();
        }
        ProjectEntity project = activeProject(report.getProjectId());
        GroupEntity group = activeGroup(report.getGroupId());
        if (project == null || group == null || !coherent(project, group)) {
            return Evaluation.denied(AiCapabilityDenialReason.RESOURCE_OUT_OF_SCOPE);
        }
        Long taskId = report.getTaskId();
        if (taskId != null) {
            TaskEntity task = taskRepository.findByIdAndDeletedFalseAndActiveTrue(taskId).orElse(null);
            if (task == null || !report.getProjectId().equals(task.getProjectId())
                    || !report.getGroupId().equals(task.getGroupId())
                    || !report.getMilestoneId().equals(task.getMilestoneId())) {
                return Evaluation.denied(AiCapabilityDenialReason.RESOURCE_OUT_OF_SCOPE);
            }
        }
        if (actor.hasRole("STUDENT")) {
            GroupRole role = groupMemberRepository.findActiveRoleByGroupIdAndUserId(group.getId(), actor.getId())
                    .orElse(null);
            if (role == GroupRole.LEADER && !actor.getId().equals(report.getSubmittedById())) {
                return allow(AiResourceType.REPORT, report.getId(), group.getLab().getId(), project.getId(),
                        group.getId(), taskId, AiResourceScope.GROUP_LEADER,
                        AiCapabilityEvidence.GROUP_LEADERSHIP);
            }
        }
        if (actor.hasRole("LAB_MANAGER")) {
            if (laboratoryRepository.existsByIdAndManagerIdAndActiveTrueAndDeletedFalse(
                    group.getLab().getId(), actor.getId())) {
                return allow(AiResourceType.REPORT, report.getId(), group.getLab().getId(), project.getId(),
                        group.getId(), taskId, AiResourceScope.MANAGED_LAB, AiCapabilityEvidence.MANAGED_LAB);
            }
        }
        if (actor.hasRole("STUDENT")) {
            return Evaluation.denied(AiCapabilityDenialReason.NOT_GROUP_LEADER);
        }
        if (actor.hasRole("LAB_MANAGER")) {
            return Evaluation.denied(AiCapabilityDenialReason.NOT_MANAGED_LAB);
        }
        return Evaluation.denied(AiCapabilityDenialReason.ROLE_NOT_ALLOWED);
    }

    private ProjectEntity activeProject(Long projectId) {
        return projectRepository.findByIdAndDeletedFalseAndActiveTrue(projectId)
                .filter(project -> Boolean.TRUE.equals(project.getActive()))
                .filter(project -> !Boolean.TRUE.equals(project.getDeleted()))
                .filter(project -> usableLab(project.getLab()))
                .orElse(null);
    }

    private GroupEntity activeGroup(Long groupId) {
        return groupRepository.findByIdAndDeletedFalseAndActiveTrue(groupId)
                .filter(group -> Boolean.TRUE.equals(group.getActive()))
                .filter(group -> !Boolean.TRUE.equals(group.getDeleted()))
                .filter(group -> usableLab(group.getLab()))
                .orElse(null);
    }

    private TaskEntity activeTask(Long taskId) {
        return taskRepository.findByIdAndDeletedFalseAndActiveTrue(taskId).orElse(null);
    }

    private TaskScope taskScope(TaskEntity task) {
        if (task.getProjectId() == null || task.getGroupId() == null) {
            return null;
        }
        ProjectEntity project = activeProject(task.getProjectId());
        GroupEntity group = activeGroup(task.getGroupId());
        return project != null && group != null && coherent(project, group)
                ? new TaskScope(task, project, group) : null;
    }

    private static boolean coherent(ProjectEntity project, GroupEntity group) {
        if (project.getId() == null || group.getId() == null || !usableLab(project.getLab())
                || !usableLab(group.getLab()) || !project.getLab().getId().equals(group.getLab().getId())) {
            return false;
        }
        if (group.getProject() != null && !project.getId().equals(group.getProject().getId())) {
            return false;
        }
        if (project.getGroup() != null && !group.getId().equals(project.getGroup().getId())) {
            return false;
        }
        return group.getProject() != null || project.getGroup() != null;
    }

    private static boolean usableLab(Laboratory lab) {
        return lab != null && lab.getId() != null && Boolean.TRUE.equals(lab.getActive())
                && !Boolean.TRUE.equals(lab.getDeleted());
    }

    private static Long projectId(GroupEntity group) {
        return group.getProject() == null ? null : group.getProject().getId();
    }

    private static Evaluation allowTask(TaskScope scope, AiResourceScope resourceScope,
                                        AiCapabilityEvidence evidence) {
        return allow(AiResourceType.TASK, scope.task().getId(), scope.group().getLab().getId(),
                scope.project().getId(), scope.group().getId(), scope.task().getId(), resourceScope, evidence);
    }

    private static Evaluation allow(AiResourceType type, Long id, Long labId, Long projectId,
                                    Long groupId, Long taskId, AiResourceScope scope,
                                    AiCapabilityEvidence policyEvidence) {
        return Evaluation.allowed(new AiCapabilityDecision.ResolvedResource(
                        type, id, labId, projectId, groupId, taskId, scope),
                Set.of(AiCapabilityEvidence.DERIVED_RESOURCE, policyEvidence));
    }

    private static Evaluation unavailable() {
        return Evaluation.denied(AiCapabilityDenialReason.RESOURCE_UNAVAILABLE);
    }

    private record TaskScope(TaskEntity task, ProjectEntity project, GroupEntity group) {
    }
}
