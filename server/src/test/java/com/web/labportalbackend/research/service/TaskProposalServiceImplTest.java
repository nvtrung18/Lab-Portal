package com.web.labportalbackend.research.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.labportalbackend.admin.audit.enums.AuditAction;
import com.web.labportalbackend.admin.audit.enums.AuditModule;
import com.web.labportalbackend.admin.audit.service.AuditLogService;
import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.enums.UserStatus;
import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.lab.entity.Laboratory;
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
import com.web.labportalbackend.research.service.impl.TaskProposalServiceImpl;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TaskProposalServiceImplTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final GroupRepository groupRepository = mock(GroupRepository.class);
    private final LaboratoryRepository laboratoryRepository = mock(LaboratoryRepository.class);
    private final GroupMemberRepository groupMemberRepository = mock(GroupMemberRepository.class);
    private final MilestoneRepository milestoneRepository = mock(MilestoneRepository.class);
    private final TaskRepository taskRepository = mock(TaskRepository.class);
    private final TaskProposalRepository taskProposalRepository = mock(TaskProposalRepository.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final EntityManager entityManager = mock(EntityManager.class);
    private ObjectMapper objectMapper;
    private TaskProposalServiceImpl service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = service(objectMapper);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("student", null, List.of()));
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @EnumSource(GroupRole.class)
    void memberAndLeaderSubmissionsUseLockedActorAndPersistFrozenServerState(GroupRole groupRole)
            throws Exception {
        Fixture fixture = stubValidScope(groupRole, false);
        CreateTaskProposalRequest request = request();
        when(taskProposalRepository.saveAndFlush(any())).thenAnswer(invocation -> saved(invocation.getArgument(0)));

        TaskProposalResponse response = service.submit(request);

        TaskProposalEntity proposal = capturedProposal();
        assertEquals(fixture.actor().getId(), proposal.getProposedById());
        assertEquals(TaskProposalStatus.PENDING, proposal.getStatus());
        assertFalse(proposal.getAssistedByAi());
        assertNull(proposal.getAiActionSuggestionId());
        assertNull(proposal.getReviewedById());
        assertNull(proposal.getReason());
        assertNull(proposal.getReviewedAt());
        JsonNode payload = objectMapper.readTree(proposal.getPayloadJson());
        assertEquals(9, payload.size());
        assertEquals("Proposal title", payload.path("title").asText());
        assertEquals("MEDIUM", payload.path("priority").asText());
        assertEquals("TASK", payload.path("type").asText());
        assertTrue(payload.path("milestoneId").isNull());
        assertTrue(payload.path("parentTaskId").isNull());
        assertTrue(payload.path("description").isNull());
        assertTrue(payload.path("dueDate").isNull());
        assertEquals(proposal.getId(), response.getId());
        assertEquals(fixture.actor().getId(), response.getProposedById());
        assertEquals("Proposal title", response.getTitle());
        assertEquals(TaskProposalStatus.PENDING, response.getStatus());
        verify(auditLogService).log(
                fixture.actor(),
                AuditAction.CREATE_TASK_PROPOSAL,
                AuditModule.RESEARCH,
                "TASK_PROPOSAL",
                proposal.getId(),
                "Submitted task proposal"
        );
    }

    @Test
    void acquiresAllFinalLocksInFixedOrderBeforeSaving() {
        Fixture fixture = stubValidScope(GroupRole.MEMBER, false);
        MilestoneEntity milestone = MilestoneEntity.builder()
                .project(fixture.project())
                .group(fixture.group())
                .build();
        milestone.setId(40L);
        TaskEntity parent = TaskEntity.builder()
                .projectId(fixture.project().getId())
                .groupId(fixture.group().getId())
                .title("Parent")
                .build();
        parent.setId(50L);
        when(milestoneRepository.findByIdForProposalSubmission(40L)).thenReturn(Optional.of(milestone));
        when(taskRepository.findByIdForProposalSubmission(50L)).thenReturn(Optional.of(parent));
        when(taskProposalRepository.saveAndFlush(any())).thenAnswer(invocation -> saved(invocation.getArgument(0)));
        CreateTaskProposalRequest request = request();
        request.setMilestoneId(40L);
        request.setParentTaskId(50L);

        service.submit(request);

        InOrder order = inOrder(
                userRepository,
                projectRepository,
                groupRepository,
                laboratoryRepository,
                groupMemberRepository,
                milestoneRepository,
                taskRepository,
                taskProposalRepository,
                auditLogService
        );
        order.verify(userRepository).findByUsername("student");
        order.verify(userRepository).findByIdForStatusAuthorization(fixture.actor().getId());
        order.verify(projectRepository).findByIdForStatusAuthorization(fixture.project().getId());
        order.verify(groupRepository).findByIdForStatusAuthorization(fixture.group().getId());
        order.verify(laboratoryRepository).findByIdForStatusAuthorization(fixture.lab().getId());
        order.verify(groupMemberRepository).findActiveForStatusAuthorization(
                fixture.group().getId(), fixture.actor().getId());
        order.verify(milestoneRepository).findByIdForProposalSubmission(40L);
        order.verify(taskRepository).findByIdForProposalSubmission(50L);
        order.verify(taskProposalRepository).saveAndFlush(any());
        order.verify(auditLogService).log(
                any(), any(), any(), any(), any(), any());
    }

    @Test
    void acceptsLegacyProjectGroupAssociation() {
        Fixture fixture = stubValidScope(GroupRole.MEMBER, true);
        when(taskProposalRepository.saveAndFlush(any())).thenAnswer(invocation -> saved(invocation.getArgument(0)));

        TaskProposalResponse response = service.submit(request());

        assertEquals(fixture.group().getId(), response.getGroupId());
    }

    @ParameterizedTest
    @EnumSource(TaskType.class)
    void acceptsEveryCurrentTaskType(TaskType type) throws Exception {
        stubValidScope(GroupRole.MEMBER, false);
        when(taskProposalRepository.saveAndFlush(any())).thenAnswer(invocation -> saved(invocation.getArgument(0)));
        CreateTaskProposalRequest request = request();
        request.setType(type);

        service.submit(request);

        assertEquals(type.name(), objectMapper.readTree(capturedProposal().getPayloadJson())
                .path("type").asText());
    }

    @ParameterizedTest
    @EnumSource(TaskPriority.class)
    void acceptsEveryCurrentTaskPriority(TaskPriority priority) throws Exception {
        stubValidScope(GroupRole.MEMBER, false);
        when(taskProposalRepository.saveAndFlush(any())).thenAnswer(invocation -> saved(invocation.getArgument(0)));
        CreateTaskProposalRequest request = request();
        request.setPriority(priority);

        service.submit(request);

        assertEquals(priority.name(), objectMapper.readTree(capturedProposal().getPayloadJson())
                .path("priority").asText());
    }

    @ParameterizedTest
    @ValueSource(strings = {"inactive", "deleted", "suspended", "non-student"})
    void rejectsUnusableOrNonStudentLockedActorWithoutScopeReadsOrWrites(String state) {
        User actor = actor();
        when(userRepository.findByUsername("student")).thenReturn(Optional.of(actor));
        when(userRepository.findByIdForStatusAuthorization(actor.getId())).thenReturn(Optional.of(actor));
        switch (state) {
            case "inactive" -> actor.setActive(false);
            case "deleted" -> actor.setDeleted(true);
            case "suspended" -> actor.setStatus(UserStatus.SUSPENDED);
            case "non-student" -> {
                actor.getRoles().clear();
                actor.addRole(new Role("RESEARCHER", "Researcher"));
            }
            default -> throw new AssertionError("Unexpected actor state: " + state);
        }

        assertThrows(AccessDeniedException.class, () -> service.submit(request()));

        verifyNoInteractions(projectRepository, groupRepository, laboratoryRepository,
                groupMemberRepository, taskProposalRepository, auditLogService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ADMIN", "LAB_MANAGER", "RESEARCHER",
            "LEADER", "LAB_LEADER", "UNRELATED"
    })
    void rejectsStudentCombinedWithAnyOtherPersistedRoleWithoutScopeReadsOrWrites(
            String otherRole
    ) {
        User actor = actor();
        actor.addRole(new Role(otherRole, otherRole));
        when(userRepository.findByUsername("student")).thenReturn(Optional.of(actor));
        when(userRepository.findByIdForStatusAuthorization(actor.getId()))
                .thenReturn(Optional.of(actor));

        assertThrows(AccessDeniedException.class, () -> service.submit(request()));

        verifyNoInteractions(projectRepository, groupRepository, laboratoryRepository,
                groupMemberRepository, taskProposalRepository, auditLogService);
    }

    @Test
    void unauthenticatedTokenIsDeniedBeforeActorOrScopeReadsAndWrites() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("student", null));

        assertThrows(AccessDeniedException.class, () -> service.submit(request()));

        verifyNoInteractions(
                userRepository,
                projectRepository,
                groupRepository,
                laboratoryRepository,
                groupMemberRepository,
                milestoneRepository,
                taskRepository,
                taskProposalRepository,
                auditLogService
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "actor", "laboratory", "membership", "milestone", "parent"
    })
    void emptyFinalLockedLookupRejectsWithoutProposalOrAudit(String lockedRow) {
        Fixture fixture = stubValidScope(GroupRole.MEMBER, false);
        CreateTaskProposalRequest submission = request();
        switch (lockedRow) {
            case "actor" -> when(userRepository.findByIdForStatusAuthorization(fixture.actor().getId()))
                    .thenReturn(Optional.empty());
            case "laboratory" -> when(laboratoryRepository.findByIdForStatusAuthorization(fixture.lab().getId()))
                    .thenReturn(Optional.empty());
            case "membership" -> when(groupMemberRepository.findActiveForStatusAuthorization(
                    fixture.group().getId(), fixture.actor().getId())).thenReturn(Optional.empty());
            case "milestone" -> {
                submission.setMilestoneId(40L);
                when(milestoneRepository.findByIdForProposalSubmission(40L))
                        .thenReturn(Optional.empty());
            }
            case "parent" -> {
                submission.setParentTaskId(50L);
                when(taskRepository.findByIdForProposalSubmission(50L))
                        .thenReturn(Optional.empty());
            }
            default -> throw new AssertionError("Unexpected locked row: " + lockedRow);
        }

        assertThrows(RuntimeException.class, () -> service.submit(submission));

        verify(taskProposalRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "missing-project", "inactive-project", "deleted-project",
            "missing-group", "inactive-group", "deleted-group"
    })
    void missingInactiveOrDeletedProjectOrGroupIsNotFoundWithoutWrites(
            String unavailableScope
    ) {
        Fixture fixture = stubValidScope(GroupRole.MEMBER, false);
        if (unavailableScope.endsWith("project")) {
            when(projectRepository.findByIdForStatusAuthorization(fixture.project().getId()))
                    .thenReturn(Optional.empty());
        } else {
            when(groupRepository.findByIdForStatusAuthorization(fixture.group().getId()))
                    .thenReturn(Optional.empty());
        }

        assertThrows(ResourceNotFoundException.class, () -> service.submit(request()));

        verify(taskProposalRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void rejectsConflictingProjectOrCrossLabBeforeMembershipAndWrites() {
        Fixture fixture = stubValidScope(GroupRole.MEMBER, false);
        ProjectEntity otherProject = ProjectEntity.builder()
                .lab(fixture.lab())
                .title("Other")
                .build();
        otherProject.setId(99L);
        fixture.group().setProject(otherProject);

        assertThrows(IllegalArgumentException.class, () -> service.submit(request()));

        verify(laboratoryRepository, never()).findByIdForStatusAuthorization(anyLong());
        verifyNoInteractions(groupMemberRepository, taskProposalRepository, auditLogService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"cross-lab", "null-project-lab", "null-group-lab"})
    void rejectsCrossLabOrNullLabScopeBeforeMembershipAndWrites(String invalidScope) {
        Fixture fixture = stubValidScope(GroupRole.MEMBER, false);
        switch (invalidScope) {
            case "cross-lab" -> {
                Laboratory otherLab = new Laboratory();
                otherLab.setId(11L);
                fixture.group().setLab(otherLab);
            }
            case "null-project-lab" -> fixture.project().setLab(null);
            case "null-group-lab" -> fixture.group().setLab(null);
            default -> throw new AssertionError("Unexpected scope: " + invalidScope);
        }

        assertThrows(IllegalArgumentException.class, () -> service.submit(request()));

        verify(laboratoryRepository, never()).findByIdForStatusAuthorization(anyLong());
        verifyNoInteractions(groupMemberRepository, taskProposalRepository, auditLogService);
    }

    @Test
    void rejectsMissingMilestoneWithoutWrites() {
        stubValidScope(GroupRole.MEMBER, false);
        when(milestoneRepository.findByIdForProposalSubmission(40L))
                .thenReturn(Optional.empty());
        CreateTaskProposalRequest milestoneRequest = request();
        milestoneRequest.setMilestoneId(40L);

        assertThrows(ResourceNotFoundException.class, () -> service.submit(milestoneRequest));

        verify(taskProposalRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void rejectsWrongMilestoneScopeWithoutWrites() {
        Fixture fixture = stubValidScope(GroupRole.MEMBER, false);
        ProjectEntity other = ProjectEntity.builder().lab(fixture.lab()).title("Other").build();
        other.setId(91L);
        MilestoneEntity milestone = MilestoneEntity.builder().project(other).build();
        milestone.setId(40L);
        when(milestoneRepository.findByIdForProposalSubmission(40L)).thenReturn(Optional.of(milestone));
        CreateTaskProposalRequest milestoneRequest = request();
        milestoneRequest.setMilestoneId(40L);

        assertThrows(IllegalArgumentException.class, () -> service.submit(milestoneRequest));

        verify(taskProposalRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void rejectsWrongMilestoneGroupWithoutWrites() {
        Fixture fixture = stubValidScope(GroupRole.MEMBER, false);
        GroupEntity otherGroup = GroupEntity.builder()
                .lab(fixture.lab())
                .project(fixture.project())
                .name("Other group")
                .build();
        otherGroup.setId(39L);
        MilestoneEntity milestone = MilestoneEntity.builder()
                .project(fixture.project())
                .group(otherGroup)
                .build();
        milestone.setId(40L);
        when(milestoneRepository.findByIdForProposalSubmission(40L))
                .thenReturn(Optional.of(milestone));
        CreateTaskProposalRequest milestoneRequest = request();
        milestoneRequest.setMilestoneId(40L);

        assertThrows(IllegalArgumentException.class, () -> service.submit(milestoneRequest));

        verify(taskProposalRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void rejectsMissingParentWithoutWrites() {
        stubValidScope(GroupRole.MEMBER, false);
        when(taskRepository.findByIdForProposalSubmission(50L))
                .thenReturn(Optional.empty());
        CreateTaskProposalRequest parentRequest = request();
        parentRequest.setParentTaskId(50L);

        assertThrows(ResourceNotFoundException.class, () -> service.submit(parentRequest));

        verify(taskProposalRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"project", "group"})
    void rejectsIndependentParentProjectOrGroupMismatchWithoutWrites(String mismatchedField) {
        Fixture fixture = stubValidScope(GroupRole.MEMBER, false);
        TaskEntity parent = TaskEntity.builder()
                .projectId("project".equals(mismatchedField) ? 91L : fixture.project().getId())
                .groupId("group".equals(mismatchedField) ? 92L : fixture.group().getId())
                .title("Wrong parent")
                .build();
        parent.setId(50L);
        when(taskRepository.findByIdForProposalSubmission(50L)).thenReturn(Optional.of(parent));
        CreateTaskProposalRequest parentRequest = request();
        parentRequest.setParentTaskId(50L);

        assertThrows(IllegalArgumentException.class, () -> service.submit(parentRequest));

        verify(taskProposalRepository, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void serializationFailureRemainsServerRuntimeAndWritesNothing() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("cannot serialize") { });
        service = service(failingMapper);
        User actor = actor();
        when(userRepository.findByUsername("student")).thenReturn(Optional.of(actor));

        RuntimeException failure = assertThrows(RuntimeException.class, () -> service.submit(request()));

        assertFalse(failure instanceof IllegalArgumentException);
        assertFalse(failure instanceof IllegalStateException);
        assertInstanceOf(JsonProcessingException.class, failure.getCause());
        verify(userRepository).findByUsername("student");
        verify(userRepository, never()).findByIdForStatusAuthorization(anyLong());
        verifyNoInteractions(taskProposalRepository, auditLogService);
    }

    @Test
    void optionalRepositoryMethodsUsePessimisticReadLocks() throws Exception {
        Lock milestoneLock = MilestoneRepository.class
                .getMethod("findByIdForProposalSubmission", Long.class)
                .getAnnotation(Lock.class);
        Lock taskLock = TaskRepository.class
                .getMethod("findByIdForProposalSubmission", Long.class)
                .getAnnotation(Lock.class);

        assertEquals(LockModeType.PESSIMISTIC_READ, milestoneLock.value());
        assertEquals(LockModeType.PESSIMISTIC_READ, taskLock.value());
    }

    private TaskProposalServiceImpl service(ObjectMapper mapper) {
        return new TaskProposalServiceImpl(
                userRepository,
                projectRepository,
                groupRepository,
                laboratoryRepository,
                groupMemberRepository,
                milestoneRepository,
                taskRepository,
                taskProposalRepository,
                auditLogService,
                mapper,
                entityManager
        );
    }

    private Fixture stubValidScope(GroupRole groupRole, boolean legacyAssociation) {
        User actor = actor();
        Laboratory lab = new Laboratory();
        lab.setId(10L);
        ProjectEntity project = ProjectEntity.builder().lab(lab).title("Project").build();
        project.setId(20L);
        GroupEntity group = GroupEntity.builder()
                .lab(lab)
                .leader(actor)
                .name("Group")
                .build();
        group.setId(30L);
        if (legacyAssociation) {
            project.setGroup(group);
        } else {
            group.setProject(project);
        }
        GroupMemberEntity membership = GroupMemberEntity.builder()
                .group(group)
                .user(actor)
                .role(groupRole)
                .build();
        membership.setId(31L);
        when(userRepository.findByUsername("student")).thenReturn(Optional.of(actor));
        when(userRepository.findByIdForStatusAuthorization(actor.getId())).thenReturn(Optional.of(actor));
        when(projectRepository.findByIdForStatusAuthorization(project.getId())).thenReturn(Optional.of(project));
        when(groupRepository.findByIdForStatusAuthorization(group.getId())).thenReturn(Optional.of(group));
        when(laboratoryRepository.findByIdForStatusAuthorization(lab.getId())).thenReturn(Optional.of(lab));
        when(groupMemberRepository.findActiveForStatusAuthorization(group.getId(), actor.getId()))
                .thenReturn(Optional.of(membership));
        return new Fixture(actor, lab, project, group);
    }

    private User actor() {
        User actor = new User();
        actor.setId(7L);
        actor.setUsername("student");
        actor.setEmail("student@example.test");
        actor.setPassword("password");
        actor.setStatus(UserStatus.ACTIVE);
        actor.setActive(true);
        actor.setDeleted(false);
        actor.addRole(new Role("STUDENT", "Student"));
        return actor;
    }

    private CreateTaskProposalRequest request() {
        CreateTaskProposalRequest request = new CreateTaskProposalRequest();
        request.setProjectId(20L);
        request.setGroupId(30L);
        request.setTitle("  Proposal title  ");
        return request;
    }

    private TaskProposalEntity saved(TaskProposalEntity proposal) {
        proposal.setId(100L);
        proposal.setCreatedAt(Instant.parse("2026-07-30T08:00:00Z"));
        proposal.setUpdatedAt(Instant.parse("2026-07-30T08:00:00Z"));
        return proposal;
    }

    private TaskProposalEntity capturedProposal() {
        org.mockito.ArgumentCaptor<TaskProposalEntity> captor =
                org.mockito.ArgumentCaptor.forClass(TaskProposalEntity.class);
        verify(taskProposalRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    private record Fixture(
            User actor,
            Laboratory lab,
            ProjectEntity project,
            GroupEntity group
    ) {
    }
}
