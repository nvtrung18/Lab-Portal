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
import com.web.labportalbackend.research.exception.TaskProposalNotificationException;
import com.web.labportalbackend.research.exception.TaskProposalReviewConflictException;
import com.web.labportalbackend.research.port.ProposalNotificationEvent;
import com.web.labportalbackend.research.port.ProposalNotificationPort;
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
import org.mockito.ArgumentCaptor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.LocalDate;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doThrow;
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
    private final ProposalNotificationPort proposalNotificationPort = mock(ProposalNotificationPort.class);
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
        ArgumentCaptor<ProposalNotificationEvent> eventCaptor =
                ArgumentCaptor.forClass(ProposalNotificationEvent.class);
        verify(proposalNotificationPort).publish(eventCaptor.capture());
        ProposalNotificationEvent event = eventCaptor.getValue();
        assertEquals(1, event.schemaVersion());
        assertEquals(ProposalNotificationEvent.ProposalNotificationType.SUBMITTED, event.type());
        assertEquals(proposal.getId(), event.proposalId());
        assertEquals(fixture.actor().getId(), event.actorId());
        assertEquals(fixture.project().getId(), event.projectId());
        assertEquals(fixture.group().getId(), event.groupId());
        assertEquals(List.of(), event.recipientUserIds());
        assertNull(event.createdTaskId());
        assertEquals(proposal.getCreatedAt(), event.occurredAt());
    }

    @Test
    void submissionDeduplicatesAndSortsExactGroupLeadersAndUsableRoleQualifiedManager() {
        Fixture fixture = stubValidScope(GroupRole.MEMBER, false);
        User manager = usableUser(11L, "manager");
        manager.addRole(new Role("LAB_MANAGER", "Lab manager"));
        fixture.lab().setManager(manager);
        when(groupMemberRepository.findActiveLeaderUserIdsForProposalNotification(
                fixture.group().getId())).thenReturn(List.of(12L, manager.getId()));
        when(taskProposalRepository.saveAndFlush(any()))
                .thenAnswer(invocation -> saved(invocation.getArgument(0)));

        service.submit(request());

        ArgumentCaptor<ProposalNotificationEvent> eventCaptor =
                ArgumentCaptor.forClass(ProposalNotificationEvent.class);
        verify(proposalNotificationPort).publish(eventCaptor.capture());
        assertEquals(List.of(11L, 12L), eventCaptor.getValue().recipientUserIds());
        assertThrows(UnsupportedOperationException.class,
                () -> eventCaptor.getValue().recipientUserIds().add(13L));
        verify(groupMemberRepository).findActiveLeaderUserIdsForProposalNotification(
                fixture.group().getId());
    }

    @Test
    void submissionExcludesUnusableOrUnqualifiedManagerAndStillPublishesEmptyRecipients() {
        Fixture fixture = stubValidScope(GroupRole.MEMBER, false);
        User manager = usableUser(11L, "manager");
        fixture.lab().setManager(manager);
        when(taskProposalRepository.saveAndFlush(any()))
                .thenAnswer(invocation -> saved(invocation.getArgument(0)));

        service.submit(request());

        ArgumentCaptor<ProposalNotificationEvent> eventCaptor =
                ArgumentCaptor.forClass(ProposalNotificationEvent.class);
        verify(proposalNotificationPort).publish(eventCaptor.capture());
        assertEquals(List.of(), eventCaptor.getValue().recipientUserIds());
    }

    @ParameterizedTest
    @ValueSource(strings = {"inactive", "deleted", "suspended"})
    void submissionExcludesUnusableRoleQualifiedManager(String state) {
        Fixture fixture = stubValidScope(GroupRole.MEMBER, false);
        User manager = usableUser(11L, "manager");
        manager.addRole(new Role("LAB_MANAGER", "Lab manager"));
        switch (state) {
            case "inactive" -> manager.setActive(false);
            case "deleted" -> manager.setDeleted(true);
            case "suspended" -> manager.setStatus(UserStatus.SUSPENDED);
            default -> throw new AssertionError("Unexpected manager state: " + state);
        }
        fixture.lab().setManager(manager);
        when(taskProposalRepository.saveAndFlush(any()))
                .thenAnswer(invocation -> saved(invocation.getArgument(0)));

        service.submit(request());

        ArgumentCaptor<ProposalNotificationEvent> eventCaptor =
                ArgumentCaptor.forClass(ProposalNotificationEvent.class);
        verify(proposalNotificationPort).publish(eventCaptor.capture());
        assertEquals(List.of(), eventCaptor.getValue().recipientUserIds());
    }

    @Test
    void submissionNotificationFailureIsCausePreservingAndOccursAfterFlushAndAudit() {
        Fixture fixture = stubValidScope(GroupRole.MEMBER, false);
        when(taskProposalRepository.saveAndFlush(any()))
                .thenAnswer(invocation -> saved(invocation.getArgument(0)));
        RuntimeException portFailure = new RuntimeException("notification backend unavailable");
        doThrow(portFailure).when(proposalNotificationPort).publish(any());

        TaskProposalNotificationException failure = assertThrows(
                TaskProposalNotificationException.class,
                () -> service.submit(request())
        );

        assertEquals(portFailure, failure.getCause());
        RuntimeException boundaryFailure = failure;
        assertFalse(boundaryFailure instanceof IllegalArgumentException);
        assertFalse(boundaryFailure instanceof IllegalStateException);
        InOrder order = inOrder(taskProposalRepository, auditLogService, proposalNotificationPort);
        order.verify(taskProposalRepository).saveAndFlush(any());
        order.verify(auditLogService).log(
                fixture.actor(), AuditAction.CREATE_TASK_PROPOSAL, AuditModule.RESEARCH,
                "TASK_PROPOSAL", 100L, "Submitted task proposal");
        order.verify(proposalNotificationPort).publish(any());
    }

    @Test
    void eventConstructionFailureIsWrappedWithExactCause() {
        stubValidScope(GroupRole.MEMBER, false);
        when(taskProposalRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            TaskProposalEntity proposal = invocation.getArgument(0);
            proposal.setCreatedAt(Instant.parse("2026-07-30T08:00:00Z"));
            return proposal;
        });

        TaskProposalNotificationException failure = assertThrows(
                TaskProposalNotificationException.class,
                () -> service.submit(request())
        );

        assertInstanceOf(NullPointerException.class, failure.getCause());
        verifyNoInteractions(proposalNotificationPort);
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

        ArgumentCaptor<ProposalNotificationEvent> eventCaptor =
                ArgumentCaptor.forClass(ProposalNotificationEvent.class);
        InOrder order = inOrder(
                userRepository,
                projectRepository,
                groupRepository,
                laboratoryRepository,
                groupMemberRepository,
                milestoneRepository,
                taskRepository,
                taskProposalRepository,
                auditLogService,
                proposalNotificationPort
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
        order.verify(proposalNotificationPort).publish(eventCaptor.capture());
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
                groupMemberRepository, taskProposalRepository, auditLogService,
                proposalNotificationPort);
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
                groupMemberRepository, taskProposalRepository, auditLogService,
                proposalNotificationPort);
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
        verifyNoInteractions(auditLogService, proposalNotificationPort);
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
        verifyNoInteractions(auditLogService, proposalNotificationPort);
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
        verifyNoInteractions(groupMemberRepository, taskProposalRepository, auditLogService,
                proposalNotificationPort);
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
        verifyNoInteractions(groupMemberRepository, taskProposalRepository, auditLogService,
                proposalNotificationPort);
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
        verifyNoInteractions(auditLogService, proposalNotificationPort);
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
        verifyNoInteractions(auditLogService, proposalNotificationPort);
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
        verifyNoInteractions(auditLogService, proposalNotificationPort);
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
        verifyNoInteractions(auditLogService, proposalNotificationPort);
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
        verifyNoInteractions(auditLogService, proposalNotificationPort);
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
        verifyNoInteractions(taskProposalRepository, auditLogService, proposalNotificationPort);
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

    @Test
    void leaderApprovalUsesFreshLockedActorAndSameLockedProposalForExactlyOneTask()
            throws Exception {
        ReviewFixture fixture = stubReviewScope(false, TaskProposalStatus.PENDING);
        when(taskRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            TaskEntity task = invocation.getArgument(0);
            task.setId(501L);
            task.setCreatedAt(Instant.parse("2026-07-30T09:00:00Z"));
            task.setUpdatedAt(Instant.parse("2026-07-30T09:00:00Z"));
            return task;
        });
        when(taskProposalRepository.saveAndFlush(fixture.proposal()))
                .thenReturn(fixture.proposal());

        TaskProposalReviewResponse response = service.approve(fixture.proposal().getId());

        ArgumentCaptor<TaskEntity> taskCaptor = ArgumentCaptor.forClass(TaskEntity.class);
        verify(taskRepository).saveAndFlush(taskCaptor.capture());
        TaskEntity task = taskCaptor.getValue();
        assertEquals(fixture.project().getId(), task.getProjectId());
        assertEquals(fixture.group().getId(), task.getGroupId());
        assertNull(task.getMilestoneId());
        assertNull(task.getParentTaskId());
        assertNull(task.getAssigneeId());
        assertEquals("Proposal title", task.getTitle());
        assertNull(task.getDescription());
        assertNull(task.getDeadline());
        assertNull(task.getDueDate());
        assertEquals(TaskStatus.BACKLOG, task.getStatus());
        assertEquals(TaskPriority.MEDIUM, task.getPriority());
        assertEquals(TaskType.TASK, task.getType());
        assertEquals(fixture.freshActor().getId(), task.getCreatedBy());
        assertEquals(0, task.getProgressPercent());
        assertEquals(TaskProposalStatus.APPROVED, fixture.proposal().getStatus());
        assertEquals(fixture.freshActor().getId(), fixture.proposal().getReviewedById());
        assertNull(fixture.proposal().getReason());
        assertEquals(fixture.proposal().getId(), response.getProposalId());
        assertEquals(501L, response.getCreatedTask().getId());
        assertEquals(fixture.freshActor().getId(), response.getReviewedById());
        verify(taskRepository, times(1)).saveAndFlush(any());
        verify(taskProposalRepository, times(1))
                .saveAndFlush(fixture.proposal());

        ArgumentCaptor<ProposalNotificationEvent> eventCaptor =
                ArgumentCaptor.forClass(ProposalNotificationEvent.class);
        InOrder order = inOrder(
                userRepository,
                entityManager,
                taskProposalRepository,
                projectRepository,
                groupRepository,
                laboratoryRepository,
                groupMemberRepository,
                taskRepository,
                auditLogService,
                proposalNotificationPort
        );
        order.verify(userRepository).findByUsername("student");
        order.verify(entityManager).clear();
        order.verify(taskProposalRepository).findByIdForReview(fixture.proposal().getId());
        order.verify(userRepository).findByIdForStatusAuthorization(fixture.freshActor().getId());
        order.verify(projectRepository).findByIdForStatusAuthorization(fixture.project().getId());
        order.verify(groupRepository).findByIdForStatusAuthorization(fixture.group().getId());
        order.verify(laboratoryRepository).findByIdForStatusAuthorization(fixture.lab().getId());
        order.verify(groupMemberRepository).findActiveForStatusAuthorization(
                fixture.group().getId(), fixture.freshActor().getId());
        order.verify(taskRepository).saveAndFlush(any());
        order.verify(taskProposalRepository).saveAndFlush(fixture.proposal());
        order.verify(auditLogService).log(
                fixture.freshActor(),
                AuditAction.REVIEW_TASK_PROPOSAL,
                AuditModule.RESEARCH,
                "TASK_PROPOSAL",
                fixture.proposal().getId(),
                "Approved task proposal",
                "{\"decision\":\"APPROVED\",\"createdTaskId\":501}"
        );
        order.verify(proposalNotificationPort).publish(eventCaptor.capture());
        ProposalNotificationEvent event = eventCaptor.getValue();
        assertEquals(ProposalNotificationEvent.ProposalNotificationType.APPROVED, event.type());
        assertEquals(List.of(8L), event.recipientUserIds());
        assertEquals(501L, event.createdTaskId());
        assertEquals(fixture.proposal().getReviewedAt(), event.occurredAt());
        verify(entityManager).clear();
    }

    @ParameterizedTest
    @ValueSource(strings = {"inactive", "deleted", "suspended"})
    void rejectionPublishesOnceWithEmptyRecipientsWhenOriginalProposerIsUnusable(String state) {
        ReviewFixture fixture = stubReviewScope(false, TaskProposalStatus.PENDING);
        User inactiveProposer = usableUser(fixture.proposal().getProposedById(), "inactive-proposer");
        switch (state) {
            case "inactive" -> inactiveProposer.setActive(false);
            case "deleted" -> inactiveProposer.setDeleted(true);
            case "suspended" -> inactiveProposer.setStatus(UserStatus.SUSPENDED);
            default -> throw new AssertionError("Unexpected proposer state: " + state);
        }
        when(userRepository.findById(fixture.proposal().getProposedById()))
                .thenReturn(Optional.of(inactiveProposer));
        when(taskProposalRepository.saveAndFlush(fixture.proposal()))
                .thenReturn(fixture.proposal());
        RejectTaskProposalRequest request = new RejectTaskProposalRequest();
        request.setReason("Not ready");

        service.reject(fixture.proposal().getId(), request);

        ArgumentCaptor<ProposalNotificationEvent> eventCaptor =
                ArgumentCaptor.forClass(ProposalNotificationEvent.class);
        verify(proposalNotificationPort, times(1)).publish(eventCaptor.capture());
        assertEquals(List.of(), eventCaptor.getValue().recipientUserIds());
    }

    @Test
    void leaderRejectionUsesAuthenticatedLeaderAndCreatesNoTask() {
        ReviewFixture fixture = stubReviewScope(false, TaskProposalStatus.PENDING);
        when(taskProposalRepository.saveAndFlush(fixture.proposal()))
                .thenReturn(fixture.proposal());
        RejectTaskProposalRequest request = new RejectTaskProposalRequest();
        request.setReason("  Outside the agreed scope  ");

        TaskProposalReviewResponse response =
                service.reject(fixture.proposal().getId(), request);

        assertEquals(TaskProposalStatus.REJECTED, fixture.proposal().getStatus());
        assertEquals(fixture.freshActor().getId(), fixture.proposal().getReviewedById());
        assertEquals("Outside the agreed scope", fixture.proposal().getReason());
        assertEquals(fixture.freshActor().getId(), response.getReviewedById());
        assertEquals("Outside the agreed scope", response.getReason());
        assertNull(response.getCreatedTask());
        verify(groupMemberRepository).findActiveForStatusAuthorization(
                fixture.group().getId(), fixture.freshActor().getId());
        verifyNoInteractions(taskRepository);
        ArgumentCaptor<ProposalNotificationEvent> eventCaptor =
                ArgumentCaptor.forClass(ProposalNotificationEvent.class);
        InOrder order = inOrder(
                taskProposalRepository,
                auditLogService,
                proposalNotificationPort
        );
        order.verify(taskProposalRepository).saveAndFlush(fixture.proposal());
        order.verify(auditLogService).log(
                fixture.freshActor(),
                AuditAction.REVIEW_TASK_PROPOSAL,
                AuditModule.RESEARCH,
                "TASK_PROPOSAL",
                fixture.proposal().getId(),
                "Rejected task proposal",
                "{\"decision\":\"REJECTED\"}"
        );
        order.verify(proposalNotificationPort).publish(eventCaptor.capture());
        ProposalNotificationEvent event = eventCaptor.getValue();
        assertEquals(ProposalNotificationEvent.ProposalNotificationType.REJECTED, event.type());
        assertEquals(List.of(8L), event.recipientUserIds());
        assertNull(event.createdTaskId());
        assertEquals(fixture.proposal().getReviewedAt(), event.occurredAt());
    }

    @Test
    void managerApprovalMapsEveryProposalFieldExactlyAndAttributesSessionActor() {
        ReviewFixture fixture = stubReviewScope(true, TaskProposalStatus.PENDING);
        LocalDate dueDate = LocalDate.of(2026, 10, 15);
        MilestoneEntity milestone = MilestoneEntity.builder()
                .project(fixture.project())
                .group(fixture.group())
                .title("Milestone")
                .build();
        milestone.setId(40L);
        TaskEntity parent = TaskEntity.builder()
                .projectId(fixture.project().getId())
                .groupId(fixture.group().getId())
                .title("Parent")
                .build();
        parent.setId(41L);
        fixture.proposal().setMilestoneId(milestone.getId());
        fixture.proposal().setPayloadJson("""
                {"projectId":20,"groupId":30,"milestoneId":40,
                 "parentTaskId":41,"title":"  Exact mapped title  ",
                 "description":"Exact mapped description","priority":"HIGH",
                 "type":"REVIEW","dueDate":"2026-10-15"}
                """);
        when(milestoneRepository.findByIdForProposalSubmission(milestone.getId()))
                .thenReturn(Optional.of(milestone));
        when(taskRepository.findByIdForProposalSubmission(parent.getId()))
                .thenReturn(Optional.of(parent));
        when(taskRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            TaskEntity task = invocation.getArgument(0);
            task.setId(502L);
            return task;
        });
        when(taskProposalRepository.saveAndFlush(fixture.proposal()))
                .thenReturn(fixture.proposal());

        TaskProposalReviewResponse response =
                service.approve(fixture.proposal().getId());

        ArgumentCaptor<TaskEntity> taskCaptor =
                ArgumentCaptor.forClass(TaskEntity.class);
        verify(taskRepository, times(1)).saveAndFlush(taskCaptor.capture());
        TaskEntity task = taskCaptor.getValue();
        assertEquals(fixture.project().getId(), task.getProjectId());
        assertEquals(fixture.group().getId(), task.getGroupId());
        assertEquals(milestone.getId(), task.getMilestoneId());
        assertEquals(parent.getId(), task.getParentTaskId());
        assertNull(task.getAssigneeId());
        assertEquals("Exact mapped title", task.getTitle());
        assertEquals("Exact mapped description", task.getDescription());
        assertEquals(dueDate, task.getDeadline());
        assertEquals(dueDate, task.getDueDate());
        assertEquals(TaskStatus.TODO, task.getStatus());
        assertEquals(TaskPriority.HIGH, task.getPriority());
        assertEquals(TaskType.REVIEW, task.getType());
        assertEquals(fixture.freshActor().getId(), task.getCreatedBy());
        assertEquals(0, task.getProgressPercent());
        assertEquals(TaskProposalStatus.APPROVED, fixture.proposal().getStatus());
        assertEquals(fixture.freshActor().getId(), fixture.proposal().getReviewedById());
        assertNull(fixture.proposal().getReason());
        assertEquals(fixture.freshActor().getId(), response.getReviewedById());
        assertEquals(502L, response.getCreatedTask().getId());
        verify(laboratoryRepository).findManagedByIdForStatusAuthorization(
                fixture.lab().getId(), fixture.freshActor().getId());
        verify(taskProposalRepository, times(1))
                .saveAndFlush(fixture.proposal());
        verify(auditLogService, times(1)).log(
                fixture.freshActor(),
                AuditAction.REVIEW_TASK_PROPOSAL,
                AuditModule.RESEARCH,
                "TASK_PROPOSAL",
                fixture.proposal().getId(),
                "Approved task proposal",
                "{\"decision\":\"APPROVED\",\"createdTaskId\":502}"
        );
    }

    @Test
    void managerRejectionNormalizesReasonAndCreatesNoTask() {
        ReviewFixture fixture = stubReviewScope(true, TaskProposalStatus.PENDING);
        when(taskProposalRepository.saveAndFlush(fixture.proposal()))
                .thenReturn(fixture.proposal());
        RejectTaskProposalRequest request = new RejectTaskProposalRequest();
        request.setReason("  Not aligned with the milestone  ");

        TaskProposalReviewResponse response =
                service.reject(fixture.proposal().getId(), request);

        assertEquals(TaskProposalStatus.REJECTED, fixture.proposal().getStatus());
        assertEquals("Not aligned with the milestone", fixture.proposal().getReason());
        assertEquals(fixture.freshActor().getId(), fixture.proposal().getReviewedById());
        assertEquals(fixture.freshActor().getId(), response.getReviewedById());
        assertNull(response.getCreatedTask());
        assertEquals("Not aligned with the milestone", response.getReason());
        verify(laboratoryRepository).findManagedByIdForStatusAuthorization(
                fixture.lab().getId(), fixture.freshActor().getId());
        verify(taskProposalRepository, times(1))
                .saveAndFlush(fixture.proposal());
        verifyNoInteractions(taskRepository);
        verify(auditLogService, times(1)).log(
                fixture.freshActor(),
                AuditAction.REVIEW_TASK_PROPOSAL,
                AuditModule.RESEARCH,
                "TASK_PROPOSAL",
                fixture.proposal().getId(),
                "Rejected task proposal",
                "{\"decision\":\"REJECTED\"}"
        );
    }

    @Test
    void studentRoleWithoutCurrentLeaderMembershipCannotApprove() {
        ReviewFixture fixture = stubReviewScope(false, TaskProposalStatus.PENDING);
        when(groupMemberRepository.findActiveForStatusAuthorization(
                fixture.group().getId(), fixture.freshActor().getId()))
                .thenReturn(Optional.empty());

        assertThrows(
                AccessDeniedException.class,
                () -> service.approve(fixture.proposal().getId())
        );

        verify(groupMemberRepository).findActiveForStatusAuthorization(
                fixture.group().getId(), fixture.freshActor().getId());
        assertNoReviewWrites(fixture, TaskProposalStatus.PENDING);
    }

    @Test
    void leaderCannotRejectProposalOutsideAuthorizedGroupBoundary() {
        ReviewFixture fixture = stubReviewScope(false, TaskProposalStatus.PENDING);
        when(groupMemberRepository.findActiveForStatusAuthorization(
                fixture.group().getId(), fixture.freshActor().getId()))
                .thenReturn(Optional.empty());
        RejectTaskProposalRequest request = new RejectTaskProposalRequest();
        request.setReason("Out of scope");

        assertThrows(
                AccessDeniedException.class,
                () -> service.reject(fixture.proposal().getId(), request)
        );

        assertNoReviewWrites(fixture, TaskProposalStatus.PENDING);
    }

    @Test
    void managerRoleWithoutAuthoritativeManagedLabCannotReject() {
        ReviewFixture fixture = stubReviewScope(true, TaskProposalStatus.PENDING);
        when(laboratoryRepository.findManagedByIdForStatusAuthorization(
                fixture.lab().getId(), fixture.freshActor().getId()))
                .thenReturn(Optional.empty());
        RejectTaskProposalRequest request = new RejectTaskProposalRequest();
        request.setReason("Out of scope");

        assertThrows(
                AccessDeniedException.class,
                () -> service.reject(fixture.proposal().getId(), request)
        );

        verify(laboratoryRepository).findManagedByIdForStatusAuthorization(
                fixture.lab().getId(), fixture.freshActor().getId());
        verify(groupMemberRepository).findActiveForStatusAuthorization(
                fixture.group().getId(), fixture.freshActor().getId());
        assertNoReviewWrites(fixture, TaskProposalStatus.PENDING);
    }

    @Test
    void dualCapabilityActorFallsBackToLeaderWhenManagerScopeIsInvalid() {
        ReviewFixture fixture = stubReviewScope(true, TaskProposalStatus.PENDING);
        when(laboratoryRepository.findManagedByIdForStatusAuthorization(
                fixture.lab().getId(), fixture.freshActor().getId()))
                .thenReturn(Optional.empty());
        GroupMemberEntity membership = GroupMemberEntity.builder()
                .group(fixture.group())
                .user(fixture.freshActor())
                .role(GroupRole.LEADER)
                .build();
        membership.setId(31L);
        when(groupMemberRepository.findActiveForStatusAuthorization(
                fixture.group().getId(), fixture.freshActor().getId()))
                .thenReturn(Optional.of(membership));
        when(taskRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            TaskEntity task = invocation.getArgument(0);
            task.setId(503L);
            return task;
        });
        when(taskProposalRepository.saveAndFlush(fixture.proposal()))
                .thenReturn(fixture.proposal());

        TaskProposalReviewResponse response =
                service.approve(fixture.proposal().getId());

        assertEquals(TaskProposalStatus.APPROVED, response.getStatus());
        assertEquals(fixture.freshActor().getId(), response.getReviewedById());
        verify(laboratoryRepository).findManagedByIdForStatusAuthorization(
                fixture.lab().getId(), fixture.freshActor().getId());
        verify(groupMemberRepository).findActiveForStatusAuthorization(
                fixture.group().getId(), fixture.freshActor().getId());
        verify(taskRepository, times(1)).saveAndFlush(any());
        verify(taskProposalRepository, times(1))
                .saveAndFlush(fixture.proposal());
        verify(auditLogService, times(1)).log(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void staleAttachedActorIsClearedAndFreshLockedRoleControlsAuthorization() {
        ReviewFixture fixture = stubReviewScope(false, TaskProposalStatus.PENDING);
        User staleActor = fixture.initialActor();
        staleActor.addRole(new Role("LAB_MANAGER", "Lab manager"));
        fixture.freshActor().getRoles().clear();
        when(groupMemberRepository.findActiveForStatusAuthorization(
                fixture.group().getId(), fixture.freshActor().getId()))
                .thenReturn(Optional.empty());

        assertThrows(
                AccessDeniedException.class,
                () -> service.approve(fixture.proposal().getId())
        );

        InOrder order = inOrder(userRepository, entityManager, taskProposalRepository);
        order.verify(userRepository).findByUsername("student");
        order.verify(entityManager).clear();
        order.verify(taskProposalRepository).findByIdForReview(fixture.proposal().getId());
        order.verify(userRepository).findByIdForStatusAuthorization(fixture.freshActor().getId());
        verify(taskProposalRepository, never()).saveAndFlush(any());
        verifyNoInteractions(taskRepository, auditLogService, proposalNotificationPort);
    }

    @Test
    void authorizationIsCheckedBeforeTerminalConflict() {
        ReviewFixture fixture = stubReviewScope(false, TaskProposalStatus.APPROVED);
        when(groupMemberRepository.findActiveForStatusAuthorization(
                fixture.group().getId(), fixture.freshActor().getId()))
                .thenReturn(Optional.empty());

        assertThrows(
                AccessDeniedException.class,
                () -> service.approve(fixture.proposal().getId())
        );

        verify(taskProposalRepository, never()).saveAndFlush(any());
        verifyNoInteractions(taskRepository, auditLogService, proposalNotificationPort);
    }

    @Test
    void authorizedTerminalProposalReturnsConflictWithoutWrites() {
        ReviewFixture fixture = stubReviewScope(false, TaskProposalStatus.REJECTED);

        assertThrows(
                TaskProposalReviewConflictException.class,
                () -> service.approve(fixture.proposal().getId())
        );

        verify(taskProposalRepository, never()).saveAndFlush(any());
        verifyNoInteractions(taskRepository, auditLogService, proposalNotificationPort);
    }

    @Test
    void authorizedTerminalProposalCannotBeRejectedAndHasNoSideEffects() {
        ReviewFixture fixture = stubReviewScope(true, TaskProposalStatus.APPROVED);
        RejectTaskProposalRequest request = new RejectTaskProposalRequest();
        request.setReason("Too late");

        assertThrows(
                TaskProposalReviewConflictException.class,
                () -> service.reject(fixture.proposal().getId(), request)
        );

        assertNoReviewWrites(fixture, TaskProposalStatus.APPROVED);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{",
            "{\"projectId\":20,\"groupId\":30,\"title\":\"Title\",\"priority\":\"MEDIUM\",\"type\":\"TASK\",\"unknown\":true}",
            "{\"projectId\":20,\"groupId\":30,\"title\":\"Title\",\"priority\":null,\"type\":\"TASK\"}",
            "{\"projectId\":999,\"groupId\":30,\"title\":\"Title\",\"priority\":\"MEDIUM\",\"type\":\"TASK\"}",
            "{\"projectId\":\"20\",\"groupId\":30,\"milestoneId\":null,\"parentTaskId\":null,\"title\":\"Title\",\"description\":null,\"priority\":\"MEDIUM\",\"type\":\"TASK\",\"dueDate\":null}",
            "{\"projectId\":20,\"groupId\":30,\"milestoneId\":null,\"parentTaskId\":null,\"title\":\"Title\",\"description\":null,\"priority\":\"MEDIUM\",\"type\":\"TASK\"}"
    })
    void corruptStoredPayloadFailsAsServerRuntimeWithoutWrites(String payloadJson) {
        ReviewFixture fixture = stubReviewScope(false, TaskProposalStatus.PENDING);
        fixture.proposal().setPayloadJson(payloadJson);

        RuntimeException failure = assertThrows(
                RuntimeException.class,
                () -> service.approve(fixture.proposal().getId())
        );

        assertFalse(failure instanceof IllegalArgumentException);
        assertFalse(failure instanceof IllegalStateException);
        verify(taskProposalRepository, never()).saveAndFlush(any());
        verifyNoInteractions(taskRepository, auditLogService, proposalNotificationPort);
    }

    @Test
    void reviewRepositoryUsesPessimisticWriteLock() throws Exception {
        Lock reviewLock = TaskProposalRepository.class
                .getMethod("findByIdForReview", Long.class)
                .getAnnotation(Lock.class);

        assertEquals(LockModeType.PESSIMISTIC_WRITE, reviewLock.value());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "bad\u0001reason"})
    void invalidRejectionReasonFailsBeforeIdentityOrDatabaseReads(String reason) {
        RejectTaskProposalRequest request = new RejectTaskProposalRequest();
        request.setReason(reason);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.reject(100L, request)
        );

        verifyNoInteractions(
                projectRepository,
                groupRepository,
                laboratoryRepository,
                groupMemberRepository,
                milestoneRepository,
                taskRepository,
                taskProposalRepository,
                auditLogService,
                proposalNotificationPort,
                entityManager
        );
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
                proposalNotificationPort,
                mapper,
                entityManager
        );
    }

    private void assertNoReviewWrites(
            ReviewFixture fixture,
            TaskProposalStatus expectedStatus
    ) {
        assertEquals(expectedStatus, fixture.proposal().getStatus());
        assertNull(fixture.proposal().getReviewedById());
        assertNull(fixture.proposal().getReason());
        assertNull(fixture.proposal().getReviewedAt());
        verify(taskProposalRepository, never()).saveAndFlush(any());
        verifyNoInteractions(taskRepository, auditLogService, proposalNotificationPort);
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
        when(groupMemberRepository.findActiveLeaderUserIdsForProposalNotification(group.getId()))
                .thenReturn(List.of());
        return new Fixture(actor, lab, project, group);
    }

    private ReviewFixture stubReviewScope(
            boolean manager,
            TaskProposalStatus status
    ) {
        User initialActor = actor();
        User freshActor = actor();
        if (manager) {
            initialActor.addRole(new Role("LAB_MANAGER", "Lab manager"));
            freshActor.addRole(new Role("LAB_MANAGER", "Lab manager"));
        }

        Laboratory lab = new Laboratory();
        lab.setId(10L);
        lab.setManager(manager ? freshActor : null);
        ProjectEntity project = ProjectEntity.builder()
                .lab(lab)
                .title("Project")
                .build();
        project.setId(20L);
        GroupEntity group = GroupEntity.builder()
                .lab(lab)
                .leader(freshActor)
                .name("Group")
                .project(project)
                .build();
        group.setId(30L);
        GroupMemberEntity membership = GroupMemberEntity.builder()
                .group(group)
                .user(freshActor)
                .role(GroupRole.LEADER)
                .build();
        membership.setId(31L);

        TaskProposalEntity proposal = TaskProposalEntity.builder()
                .proposedById(8L)
                .projectId(project.getId())
                .groupId(group.getId())
                .milestoneId(null)
                .payloadJson("""
                        {"projectId":20,"groupId":30,"milestoneId":null,
                         "parentTaskId":null,"title":"Proposal title",
                         "description":null,"priority":"MEDIUM","type":"TASK",
                         "dueDate":null}
                        """)
                .status(status)
                .assistedByAi(false)
                .build();
        proposal.setId(100L);

        when(userRepository.findByUsername("student")).thenReturn(Optional.of(initialActor));
        when(taskProposalRepository.findByIdForReview(proposal.getId()))
                .thenReturn(Optional.of(proposal));
        when(userRepository.findByIdForStatusAuthorization(freshActor.getId()))
                .thenReturn(Optional.of(freshActor));
        when(userRepository.findById(proposal.getProposedById()))
                .thenReturn(Optional.of(usableUser(proposal.getProposedById(), "proposer")));
        when(projectRepository.findByIdForStatusAuthorization(project.getId()))
                .thenReturn(Optional.of(project));
        when(groupRepository.findByIdForStatusAuthorization(group.getId()))
                .thenReturn(Optional.of(group));
        when(laboratoryRepository.findByIdForStatusAuthorization(lab.getId()))
                .thenReturn(Optional.of(lab));
        if (manager) {
            when(laboratoryRepository.findManagedByIdForStatusAuthorization(
                    lab.getId(), freshActor.getId()))
                    .thenReturn(Optional.of(lab));
        } else {
            when(groupMemberRepository.findActiveForStatusAuthorization(
                    group.getId(), freshActor.getId()))
                    .thenReturn(Optional.of(membership));
        }
        return new ReviewFixture(
                initialActor,
                freshActor,
                lab,
                project,
                group,
                proposal
        );
    }

    private User actor() {
        User actor = usableUser(7L, "student");
        actor.addRole(new Role("STUDENT", "Student"));
        return actor;
    }

    private User usableUser(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(username + "@example.test");
        user.setPassword("password");
        user.setStatus(UserStatus.ACTIVE);
        user.setActive(true);
        user.setDeleted(false);
        return user;
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

    private record ReviewFixture(
            User initialActor,
            User freshActor,
            Laboratory lab,
            ProjectEntity project,
            GroupEntity group,
            TaskProposalEntity proposal
    ) {
    }
}
