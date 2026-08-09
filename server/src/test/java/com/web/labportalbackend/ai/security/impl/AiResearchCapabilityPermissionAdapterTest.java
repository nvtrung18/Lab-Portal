package com.web.labportalbackend.ai.security.impl;

import static com.web.labportalbackend.ai.enums.AiCapabilityDenialReason.NOT_GROUP_LEADER;
import static com.web.labportalbackend.ai.enums.AiCapabilityDenialReason.NOT_GROUP_MEMBER;
import static com.web.labportalbackend.ai.enums.AiCapabilityDenialReason.RESOURCE_OUT_OF_SCOPE;
import static com.web.labportalbackend.ai.enums.AiCapabilityDenialReason.RESOURCE_UNAVAILABLE;
import static com.web.labportalbackend.ai.enums.AiCapabilityDenialReason.ROLE_NOT_ALLOWED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.ai.enums.AiAssistantSystemRole;
import com.web.labportalbackend.ai.enums.AiCapability;
import com.web.labportalbackend.ai.enums.AiRequestedAction;
import com.web.labportalbackend.ai.enums.AiResourceType;
import com.web.labportalbackend.ai.security.AiCapabilityPermissionAdapter;
import com.web.labportalbackend.ai.service.AiCapabilityRequest;
import com.web.labportalbackend.auth.entity.Role;
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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiResearchCapabilityPermissionAdapterTest {

    @Mock TaskPermissionHelper taskPermissionHelper;
    @Mock ProjectRepository projectRepository;
    @Mock GroupRepository groupRepository;
    @Mock TaskRepository taskRepository;
    @Mock ReportRepository reportRepository;
    @Mock GroupMemberRepository groupMemberRepository;
    @Mock LaboratoryRepository laboratoryRepository;
    @InjectMocks AiResearchCapabilityPermissionAdapter adapter;

    private Laboratory lab;
    private ProjectEntity project;
    private GroupEntity group;

    @BeforeEach
    void setUp() {
        lab = lab(10L);
        project = project(20L, lab);
        group = group(30L, lab, project);
        project.setGroup(group);
    }

    @Test
    void projectSummaryUsesCurrentStudentMembership() {
        User student = user(7L, "STUDENT");
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(20L)).thenReturn(Optional.of(project));
        when(groupMemberRepository.findActiveGroupIdsByProjectIdAndUserIdAndRole(20L, 7L, GroupRole.LEADER))
                .thenReturn(java.util.List.of());
        when(groupMemberRepository.findActiveGroupIdsByProjectIdAndUserIdAndRole(20L, 7L, GroupRole.MEMBER))
                .thenReturn(java.util.List.of(30L));

        assertTrue(adapter.evaluate(student, request(AiCapability.RESEARCH_PROJECT_SUMMARY,
                AiResourceType.PROJECT, 20L, null, AiRequestedAction.READ), AiAssistantSystemRole.STUDENT).allowed());

        when(groupMemberRepository.findActiveGroupIdsByProjectIdAndUserIdAndRole(20L, 7L, GroupRole.MEMBER))
                .thenReturn(java.util.List.of());
        assertEquals(RESOURCE_OUT_OF_SCOPE, adapter.evaluate(student, request(
                AiCapability.RESEARCH_PROJECT_SUMMARY, AiResourceType.PROJECT, 20L, null,
                AiRequestedAction.READ), AiAssistantSystemRole.STUDENT).denialReason());
    }

    @Test
    void studentProjectSummaryUsesMembershipForDualRoleActor() {
        User dualRoleActor = user(7L, "STUDENT", "LAB_MANAGER");
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(20L)).thenReturn(Optional.of(project));
        when(groupMemberRepository.findActiveGroupIdsByProjectIdAndUserIdAndRole(20L, 7L, GroupRole.LEADER))
                .thenReturn(java.util.List.of());
        when(groupMemberRepository.findActiveGroupIdsByProjectIdAndUserIdAndRole(20L, 7L, GroupRole.MEMBER))
                .thenReturn(java.util.List.of(30L));

        assertTrue(adapter.evaluate(dualRoleActor, request(AiCapability.RESEARCH_PROJECT_SUMMARY,
                AiResourceType.PROJECT, 20L, null, AiRequestedAction.READ), AiAssistantSystemRole.STUDENT).allowed());

        verifyNoInteractions(taskPermissionHelper, laboratoryRepository);
    }

    @Test
    void groupSummaryUsesFreshMembershipAndDeniesAdminBypass() {
        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(30L)).thenReturn(Optional.of(group));
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(20L)).thenReturn(Optional.of(project));
        when(groupMemberRepository.findActiveRoleByGroupIdAndUserId(30L, 7L))
                .thenReturn(Optional.of(GroupRole.MEMBER), Optional.empty());
        when(laboratoryRepository.existsByIdAndManagerIdAndActiveTrueAndDeletedFalse(10L, 7L))
                .thenReturn(true);

        assertTrue(adapter.evaluate(user(7L, "STUDENT"), request(AiCapability.RESEARCH_GROUP_SUMMARY,
                AiResourceType.GROUP, 30L, null, AiRequestedAction.READ), AiAssistantSystemRole.STUDENT).allowed());
        assertEquals(NOT_GROUP_MEMBER, adapter.evaluate(user(7L, "STUDENT"), request(
                AiCapability.RESEARCH_GROUP_SUMMARY, AiResourceType.GROUP, 30L, null,
                AiRequestedAction.READ), AiAssistantSystemRole.STUDENT).denialReason());
        assertTrue(adapter.evaluate(user(7L, "STUDENT", "LAB_MANAGER"), request(
                AiCapability.RESEARCH_GROUP_SUMMARY, AiResourceType.GROUP, 30L, null,
                AiRequestedAction.READ), AiAssistantSystemRole.LAB_MANAGER).allowed());
        assertEquals(ROLE_NOT_ALLOWED, adapter.evaluate(user(1L, "ADMIN"), request(
                AiCapability.RESEARCH_GROUP_SUMMARY, AiResourceType.GROUP, 30L, null,
                AiRequestedAction.READ), AiAssistantSystemRole.ADMIN).denialReason());
    }

    @Test
    void groupSummaryCarriesProjectFromCoherentReverseCanonicalRelation() {
        group.setProject(null);
        project.setGroup(group);
        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(30L)).thenReturn(Optional.of(group));
        when(projectRepository.findByGroupIdAndDeletedFalseAndActiveTrue(30L)).thenReturn(List.of(project));
        when(groupMemberRepository.findActiveRoleByGroupIdAndUserId(30L, 7L))
                .thenReturn(Optional.of(GroupRole.MEMBER));

        AiCapabilityPermissionAdapter.Evaluation result = adapter.evaluate(user(7L, "STUDENT"), request(
                AiCapability.RESEARCH_GROUP_SUMMARY, AiResourceType.GROUP, 30L, null,
                AiRequestedAction.READ), AiAssistantSystemRole.STUDENT);

        assertTrue(result.allowed());
        assertEquals(20L, result.resolvedResource().projectId());

        project.setLab(lab(11L));
        AiCapabilityPermissionAdapter.Evaluation incoherent = adapter.evaluate(user(7L, "STUDENT"), request(
                AiCapability.RESEARCH_GROUP_SUMMARY, AiResourceType.GROUP, 30L, null,
                AiRequestedAction.READ), AiAssistantSystemRole.STUDENT);

        assertFalse(incoherent.allowed());
        assertEquals(RESOURCE_OUT_OF_SCOPE, incoherent.denialReason());
    }

    @Test
    void groupSummaryFailsClosedForAmbiguousReverseCanonicalProjects() {
        group.setProject(null);
        ProjectEntity secondProject = project(21L, lab);
        project.setGroup(group);
        secondProject.setGroup(group);
        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(30L)).thenReturn(Optional.of(group));
        when(projectRepository.findByGroupIdAndDeletedFalseAndActiveTrue(30L))
                .thenReturn(List.of(project, secondProject));

        AiCapabilityPermissionAdapter.Evaluation result = adapter.evaluate(user(7L, "STUDENT"), request(
                AiCapability.RESEARCH_GROUP_SUMMARY, AiResourceType.GROUP, 30L, null,
                AiRequestedAction.READ), AiAssistantSystemRole.STUDENT);

        assertFalse(result.allowed());
        assertEquals(RESOURCE_OUT_OF_SCOPE, result.denialReason());
        verifyNoInteractions(groupMemberRepository);
    }

    @Test
    void activeGroupMemberMayReadButCannotDraftAnUnassignedCanonicalGroupTask() {
        User student = user(7L, "STUDENT");
        TaskEntity task = task(40L, 20L, 30L, 8L);
        stubTaskScope(task);
        when(taskPermissionHelper.canViewTask(7L, task)).thenReturn(true);
        when(taskPermissionHelper.isTaskAssignee(7L, task)).thenReturn(false);
        when(groupMemberRepository.findActiveRoleByGroupIdAndUserId(30L, 7L))
                .thenReturn(Optional.of(GroupRole.MEMBER));

        AiCapabilityPermissionAdapter.Evaluation read = adapter.evaluate(student, request(
                AiCapability.RESEARCH_ASSIGNED_TASK_READ, AiResourceType.TASK, 40L, null, AiRequestedAction.READ), AiAssistantSystemRole.STUDENT);
        AiCapabilityPermissionAdapter.Evaluation draft = adapter.evaluate(student, request(
                AiCapability.RESEARCH_TASK_SUGGESTION_DRAFT, AiResourceType.TASK, 40L, null,
                AiRequestedAction.DRAFT), AiAssistantSystemRole.STUDENT);

        assertTrue(read.allowed());
        assertEquals(com.web.labportalbackend.ai.enums.AiResourceScope.GROUP_MEMBER,
                read.resolvedResource().effectiveScope());
        assertFalse(draft.allowed());
        assertEquals(RESOURCE_OUT_OF_SCOPE, draft.denialReason());
    }

    @Test
    void assignedTaskCannotUseAssignmentAcrossForgedProjectGroupScope() {
        TaskEntity task = task(40L, 20L, 30L, 7L);
        GroupEntity contradictoryGroup = group(30L, lab, project(21L, lab));
        when(taskRepository.findByIdAndDeletedFalseAndActiveTrue(40L)).thenReturn(Optional.of(task));
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(20L)).thenReturn(Optional.of(project));
        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(30L)).thenReturn(Optional.of(contradictoryGroup));

        AiCapabilityPermissionAdapter.Evaluation result = adapter.evaluate(user(7L, "STUDENT"), request(
                AiCapability.RESEARCH_ASSIGNED_TASK_READ, AiResourceType.TASK, 40L, null,
                AiRequestedAction.READ), AiAssistantSystemRole.STUDENT);

        assertFalse(result.allowed());
        assertEquals(RESOURCE_OUT_OF_SCOPE, result.denialReason());
    }

    @Test
    void taskCapabilitiesCoarsenUnavailableTasksWithoutPermissionChecks() {
        assertUnavailableTask(AiCapability.RESEARCH_ASSIGNED_TASK_READ, "missing task");
        assertUnavailableTask(AiCapability.RESEARCH_ASSIGNED_TASK_READ, "inactive task");
        assertUnavailableTask(AiCapability.RESEARCH_ASSIGNED_TASK_READ, "deleted task");
        assertUnavailableTask(AiCapability.RESEARCH_TASK_SUGGESTION_DRAFT, "missing task");
        assertUnavailableTask(AiCapability.RESEARCH_TASK_SUGGESTION_DRAFT, "inactive task");
        assertUnavailableTask(AiCapability.RESEARCH_TASK_SUGGESTION_DRAFT, "deleted task");
    }

    @Test
    void proposalDraftRequiresOnlyStudentRoleActiveMembershipAndTrustedProjectParent() {
        User student = user(7L, "STUDENT");
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(20L)).thenReturn(Optional.of(project));
        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(30L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findActiveRoleByGroupIdAndUserId(30L, 7L))
                .thenReturn(Optional.of(GroupRole.LEADER), Optional.empty());

        assertTrue(adapter.evaluate(student, request(AiCapability.RESEARCH_TASK_PROPOSAL_DRAFT,
                AiResourceType.GROUP, 30L, parent(AiResourceType.PROJECT, 20L),
                AiRequestedAction.DRAFT), AiAssistantSystemRole.STUDENT).allowed());
        group.setProject(project(21L, lab));
        assertEquals(RESOURCE_OUT_OF_SCOPE, adapter.evaluate(student, request(
                AiCapability.RESEARCH_TASK_PROPOSAL_DRAFT, AiResourceType.GROUP, 30L,
                parent(AiResourceType.PROJECT, 20L), AiRequestedAction.DRAFT), AiAssistantSystemRole.STUDENT).denialReason());
        group.setProject(project);
        assertEquals(NOT_GROUP_MEMBER, adapter.evaluate(student, request(
                AiCapability.RESEARCH_TASK_PROPOSAL_DRAFT, AiResourceType.GROUP, 30L,
                parent(AiResourceType.PROJECT, 20L), AiRequestedAction.DRAFT), AiAssistantSystemRole.STUDENT).denialReason());

        assertEquals(ROLE_NOT_ALLOWED, adapter.evaluate(user(8L, "STUDENT", "LAB_MANAGER"), request(
                AiCapability.RESEARCH_TASK_PROPOSAL_DRAFT, AiResourceType.GROUP, 30L,
                parent(AiResourceType.PROJECT, 20L), AiRequestedAction.DRAFT), AiAssistantSystemRole.STUDENT).denialReason());
    }

    @Test
    void taskSuggestionRequiresAssigneeOrExistingManagerLeaderPermission() {
        User student = user(7L, "STUDENT");
        TaskEntity task = task(40L, 20L, 30L, 8L);
        stubTaskScope(task);
        when(taskPermissionHelper.canViewTask(7L, task)).thenReturn(true);
        when(taskPermissionHelper.isTaskAssignee(7L, task)).thenReturn(false);
        when(groupMemberRepository.findActiveRoleByGroupIdAndUserId(30L, 7L))
                .thenReturn(Optional.of(GroupRole.LEADER), Optional.empty());

        assertTrue(adapter.evaluate(student, request(AiCapability.RESEARCH_TASK_SUGGESTION_DRAFT,
                AiResourceType.TASK, 40L, null, AiRequestedAction.DRAFT), AiAssistantSystemRole.STUDENT).allowed());
        assertEquals(RESOURCE_OUT_OF_SCOPE, adapter.evaluate(student, request(
                AiCapability.RESEARCH_TASK_SUGGESTION_DRAFT, AiResourceType.TASK, 40L, null,
                AiRequestedAction.DRAFT), AiAssistantSystemRole.STUDENT).denialReason());
    }

    @Test
    void reportReviewRequiresActualGroupLeaderOrExactManagedLabAndCoherentTask() {
        User leader = user(7L, "STUDENT");
        TaskEntity task = task(40L, 20L, 30L, 8L);
        task.setMilestoneId(50L);
        ReportEntity report = ReportEntity.builder().projectId(20L).groupId(30L).taskId(40L)
                .milestoneId(50L).submittedById(8L).version(1).build();
        report.setId(60L);
        report.setActive(true);
        report.setDeleted(false);
        when(reportRepository.findById(60L)).thenReturn(Optional.of(report));
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(20L)).thenReturn(Optional.of(project));
        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(30L)).thenReturn(Optional.of(group));
        when(taskRepository.findByIdAndDeletedFalseAndActiveTrue(40L)).thenReturn(Optional.of(task));
        when(groupMemberRepository.findActiveRoleByGroupIdAndUserId(30L, 7L))
                .thenReturn(Optional.of(GroupRole.LEADER), Optional.empty());
        when(laboratoryRepository.existsByIdAndManagerIdAndActiveTrueAndDeletedFalse(10L, 9L))
                .thenReturn(true);
        when(laboratoryRepository.existsByIdAndManagerIdAndActiveTrueAndDeletedFalse(10L, 7L))
                .thenReturn(true);

        assertTrue(adapter.evaluate(leader, request(AiCapability.RESEARCH_REPORT_REVIEW_DRAFT,
                AiResourceType.REPORT, 60L, null, AiRequestedAction.DRAFT), AiAssistantSystemRole.STUDENT).allowed());
        group.setProject(project(21L, lab));
        assertEquals(RESOURCE_OUT_OF_SCOPE, adapter.evaluate(leader, request(
                AiCapability.RESEARCH_REPORT_REVIEW_DRAFT, AiResourceType.REPORT, 60L, null,
                AiRequestedAction.DRAFT), AiAssistantSystemRole.STUDENT).denialReason());
        group.setProject(project);
        assertEquals(NOT_GROUP_LEADER, adapter.evaluate(leader, request(
                AiCapability.RESEARCH_REPORT_REVIEW_DRAFT, AiResourceType.REPORT, 60L, null,
                AiRequestedAction.DRAFT), AiAssistantSystemRole.STUDENT).denialReason());
        assertTrue(adapter.evaluate(user(9L, "LAB_MANAGER"), request(
                AiCapability.RESEARCH_REPORT_REVIEW_DRAFT, AiResourceType.REPORT, 60L, null,
                AiRequestedAction.DRAFT), AiAssistantSystemRole.LAB_MANAGER).allowed());
        assertTrue(adapter.evaluate(user(7L, "STUDENT", "LAB_MANAGER"), request(
                AiCapability.RESEARCH_REPORT_REVIEW_DRAFT, AiResourceType.REPORT, 60L, null,
                AiRequestedAction.DRAFT), AiAssistantSystemRole.LAB_MANAGER).allowed());
    }

    private void stubTaskScope(TaskEntity task) {
        when(taskRepository.findByIdAndDeletedFalseAndActiveTrue(task.getId())).thenReturn(Optional.of(task));
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(20L)).thenReturn(Optional.of(project));
        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(30L)).thenReturn(Optional.of(group));
    }

    private void assertUnavailableTask(AiCapability capability, String scenario) {
        when(taskRepository.findByIdAndDeletedFalseAndActiveTrue(40L)).thenReturn(Optional.empty());

        AiCapabilityPermissionAdapter.Evaluation result = adapter.evaluate(user(7L, "STUDENT"),
                request(capability, AiResourceType.TASK, 40L, null,
                        capability == AiCapability.RESEARCH_ASSIGNED_TASK_READ
                                ? AiRequestedAction.READ : AiRequestedAction.DRAFT), AiAssistantSystemRole.STUDENT);

        assertFalse(result.allowed(), scenario);
        assertEquals(RESOURCE_UNAVAILABLE, result.denialReason(), scenario);
        verifyNoInteractions(taskPermissionHelper, projectRepository, groupRepository, reportRepository,
                groupMemberRepository, laboratoryRepository);
    }

    private static AiCapabilityRequest request(AiCapability capability, AiResourceType type, Long id,
                                               AiCapabilityRequest.ResourceReference parent,
                                               AiRequestedAction action) {
        return new AiCapabilityRequest(AiAssistantKey.RESEARCH_ASSISTANT, 7L, capability,
                new AiCapabilityRequest.ResourceReference(type, id), parent, action);
    }

    private static AiCapabilityRequest.ResourceReference parent(AiResourceType type, Long id) {
        return new AiCapabilityRequest.ResourceReference(type, id);
    }

    private static User user(Long id, String... roles) {
        User user = new User();
        user.setId(id);
        user.setActive(true);
        user.setDeleted(false);
        for (String role : roles) {
            user.addRole(new Role(role, role));
        }
        return user;
    }

    private static Laboratory lab(Long id) {
        Laboratory lab = new Laboratory();
        lab.setId(id);
        lab.setActive(true);
        lab.setDeleted(false);
        return lab;
    }

    private static ProjectEntity project(Long id, Laboratory lab) {
        ProjectEntity project = ProjectEntity.builder().lab(lab).title("Project").build();
        project.setId(id);
        project.setActive(true);
        project.setDeleted(false);
        return project;
    }

    private static GroupEntity group(Long id, Laboratory lab, ProjectEntity project) {
        GroupEntity group = GroupEntity.builder().lab(lab).project(project).name("Group").build();
        group.setId(id);
        group.setActive(true);
        group.setDeleted(false);
        return group;
    }

    private static TaskEntity task(Long id, Long projectId, Long groupId, Long assigneeId) {
        TaskEntity task = TaskEntity.builder().projectId(projectId).groupId(groupId).assigneeId(assigneeId)
                .title("Task").build();
        task.setId(id);
        task.setActive(true);
        task.setDeleted(false);
        return task;
    }
}
