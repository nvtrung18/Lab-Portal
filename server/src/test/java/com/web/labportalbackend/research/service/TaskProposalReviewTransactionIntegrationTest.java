package com.web.labportalbackend.research.service;

import com.web.labportalbackend.admin.audit.enums.AuditAction;
import com.web.labportalbackend.admin.audit.repository.AuditLogRepository;
import com.web.labportalbackend.admin.audit.service.AuditLogService;
import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.RoleRepository;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.dto.request.RejectTaskProposalRequest;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.GroupMemberEntity;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.entity.TaskProposalEntity;
import com.web.labportalbackend.research.enums.GroupRole;
import com.web.labportalbackend.research.enums.TaskProposalStatus;
import com.web.labportalbackend.research.exception.TaskProposalNotificationException;
import com.web.labportalbackend.research.port.ProposalNotificationPort;
import com.web.labportalbackend.research.repository.GroupMemberRepository;
import com.web.labportalbackend.research.repository.GroupRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.repository.TaskActivityRepository;
import com.web.labportalbackend.research.repository.TaskProposalRepository;
import com.web.labportalbackend.research.repository.TaskRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@SpringBootTest
class TaskProposalReviewTransactionIntegrationTest {

    @Autowired TaskProposalService taskProposalService;
    @Autowired RoleRepository roleRepository;
    @MockitoSpyBean UserRepository userRepository;
    @Autowired LaboratoryRepository laboratoryRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired GroupRepository groupRepository;
    @Autowired GroupMemberRepository groupMemberRepository;
    @MockitoSpyBean TaskRepository taskRepository;
    @MockitoSpyBean TaskProposalRepository taskProposalRepository;
    @Autowired TaskActivityRepository taskActivityRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @MockitoSpyBean AuditLogService auditLogService;
    @MockitoBean ProposalNotificationPort proposalNotificationPort;
    @Autowired PlatformTransactionManager transactionManager;
    @PersistenceContext EntityManager entityManager;

    @AfterEach
    void clearSecurityAndSpies() {
        SecurityContextHolder.clearContext();
        reset(userRepository, taskRepository, taskProposalRepository, auditLogService,
                proposalNotificationPort);
    }

    @Test
    void leaderApprovalCommitsTaskProposalAndSingleReviewAuditWithoutActivity() {
        Fixture fixture = fixture("leader-approve", false);
        Counts before = counts();
        AtomicBoolean lockedProposalManaged = new AtomicBoolean();
        AtomicReference<TaskProposalEntity> lockedProposal = new AtomicReference<>();
        doAnswer(invocation -> {
            TaskProposalEntity proposal = invocation.getArgument(0);
            lockedProposal.set(proposal);
            lockedProposalManaged.set(entityManager.contains(proposal));
            entityManager.flush();
            return proposal;
        }).when(taskProposalRepository).saveAndFlush(any());

        var response = taskProposalService.approve(fixture.proposalId());

        assertTrue(lockedProposalManaged.get());
        assertEquals(fixture.proposalId(), lockedProposal.get().getId());
        assertEquals(before.tasks() + 1, taskRepository.count());
        assertEquals(before.audits() + 1, auditLogRepository.count());
        assertEquals(before.activities(), taskActivityRepository.count());
        TaskProposalEntity proposal =
                taskProposalRepository.findById(fixture.proposalId()).orElseThrow();
        assertEquals(TaskProposalStatus.APPROVED, proposal.getStatus());
        assertEquals(fixture.actorId(), proposal.getReviewedById());
        assertNull(proposal.getReason());
        assertEquals(response.getCreatedTask().getId(),
                taskRepository.findAll().stream()
                        .filter(task -> fixture.projectId().equals(task.getProjectId()))
                        .filter(task -> fixture.groupId().equals(task.getGroupId()))
                        .reduce((first, second) -> second)
                        .orElseThrow().getId());
        var reviewAudits = auditLogRepository.findAll().stream()
                .filter(audit -> audit.getAction() == AuditAction.REVIEW_TASK_PROPOSAL)
                .filter(audit -> fixture.proposalId().equals(audit.getTargetId()))
                .toList();
        assertEquals(1, reviewAudits.size());
        assertTrue(reviewAudits.getFirst().getMetadataJson()
                .contains("\"decision\":\"APPROVED\""));
        assertTrue(reviewAudits.getFirst().getMetadataJson()
                .contains("\"createdTaskId\":" + response.getCreatedTask().getId()));
        assertFalse(reviewAudits.getFirst().getMetadataJson().contains("Proposal title"));
        verify(proposalNotificationPort, times(1)).publish(any());
    }

    @Test
    void managerRejectionCommitsDecisionAndAuditWithoutTaskOrActivity() {
        Fixture fixture = fixture("manager-reject", true);
        Counts before = counts();
        RejectTaskProposalRequest request = new RejectTaskProposalRequest();
        request.setReason("  Needs a narrower scope  ");

        var response = taskProposalService.reject(fixture.proposalId(), request);

        assertEquals(before.tasks(), taskRepository.count());
        assertEquals(before.audits() + 1, auditLogRepository.count());
        assertEquals(before.activities(), taskActivityRepository.count());
        TaskProposalEntity proposal =
                taskProposalRepository.findById(fixture.proposalId()).orElseThrow();
        assertEquals(TaskProposalStatus.REJECTED, proposal.getStatus());
        assertEquals(fixture.actorId(), proposal.getReviewedById());
        assertEquals("Needs a narrower scope", proposal.getReason());
        assertNull(response.getCreatedTask());
        var audit = auditLogRepository.findAll().stream()
                .filter(row -> row.getAction() == AuditAction.REVIEW_TASK_PROPOSAL)
                .filter(row -> fixture.proposalId().equals(row.getTargetId()))
                .findFirst()
                .orElseThrow();
        assertEquals("{\"decision\":\"REJECTED\"}", audit.getMetadataJson());
        assertFalse(audit.getMetadataJson().contains(proposal.getReason()));
        verify(proposalNotificationPort, times(1)).publish(any());
    }

    @Test
    void approvalNotificationFailureRollsBackTaskProposalAuditAndActivity() {
        Fixture fixture = fixture("approval-notification-failure", false);
        Counts before = counts();
        RuntimeException portFailure = new RuntimeException("approval notification failed");
        doThrow(portFailure).when(proposalNotificationPort).publish(any());

        TaskProposalNotificationException failure = assertThrows(
                TaskProposalNotificationException.class,
                () -> taskProposalService.approve(fixture.proposalId())
        );

        assertSame(portFailure, failure.getCause());
        assertCounts(before);
        TaskProposalEntity proposal =
                taskProposalRepository.findById(fixture.proposalId()).orElseThrow();
        assertEquals(TaskProposalStatus.PENDING, proposal.getStatus());
        assertNull(proposal.getReviewedById());
        assertNull(proposal.getReviewedAt());
        assertNull(proposal.getReason());
        verify(proposalNotificationPort).publish(any());
    }

    @Test
    void rejectionNotificationFailureRollsBackProposalAuditTaskAndActivity() {
        Fixture fixture = fixture("rejection-notification-failure", true);
        Counts before = counts();
        RejectTaskProposalRequest request = new RejectTaskProposalRequest();
        request.setReason("Not ready");
        RuntimeException portFailure = new RuntimeException("rejection notification failed");
        doThrow(portFailure).when(proposalNotificationPort).publish(any());

        TaskProposalNotificationException failure = assertThrows(
                TaskProposalNotificationException.class,
                () -> taskProposalService.reject(fixture.proposalId(), request)
        );

        assertSame(portFailure, failure.getCause());
        assertCounts(before);
        TaskProposalEntity proposal =
                taskProposalRepository.findById(fixture.proposalId()).orElseThrow();
        assertEquals(TaskProposalStatus.PENDING, proposal.getStatus());
        assertNull(proposal.getReviewedById());
        assertNull(proposal.getReviewedAt());
        assertNull(proposal.getReason());
        verify(proposalNotificationPort).publish(any());
    }

    @Test
    void taskSaveFailureLeavesProposalPendingAndWritesNoReviewAudit() {
        Fixture fixture = fixture("task-failure", false);
        Counts before = counts();
        doThrow(new RuntimeException("task save failed"))
                .when(taskRepository).saveAndFlush(any());

        assertThrows(
                RuntimeException.class,
                () -> taskProposalService.approve(fixture.proposalId())
        );

        assertCounts(before);
        assertEquals(TaskProposalStatus.PENDING,
                taskProposalRepository.findById(fixture.proposalId())
                        .orElseThrow().getStatus());
    }

    @Test
    void proposalFlushFailureRollsBackNewTask() {
        Fixture fixture = fixture("proposal-failure", false);
        Counts before = counts();
        doThrow(new RuntimeException("proposal flush failed"))
                .when(taskProposalRepository).saveAndFlush(any());

        assertThrows(
                RuntimeException.class,
                () -> taskProposalService.approve(fixture.proposalId())
        );

        assertCounts(before);
        assertEquals(TaskProposalStatus.PENDING,
                taskProposalRepository.findById(fixture.proposalId())
                        .orElseThrow().getStatus());
    }

    @Test
    void auditFailureAfterPersistenceAttemptRollsBackApproval() {
        Fixture fixture = fixture("approval-audit-failure", false);
        Counts before = counts();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new RuntimeException("audit unavailable");
        }).when(auditLogService)
                .log(any(), any(), any(), any(), any(), any(), any());

        assertThrows(
                RuntimeException.class,
                () -> taskProposalService.approve(fixture.proposalId())
        );

        assertCounts(before);
        assertEquals(TaskProposalStatus.PENDING,
                taskProposalRepository.findById(fixture.proposalId())
                        .orElseThrow().getStatus());
        verify(auditLogService).log(
                any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void auditFailureRollsBackRejection() {
        Fixture fixture = fixture("rejection-audit-failure", true);
        Counts before = counts();
        RejectTaskProposalRequest request = new RejectTaskProposalRequest();
        request.setReason("Not ready");
        doThrow(new RuntimeException("audit unavailable"))
                .when(auditLogService)
                .log(any(), any(), any(), any(), any(), any(), any());

        assertThrows(
                RuntimeException.class,
                () -> taskProposalService.reject(fixture.proposalId(), request)
        );

        assertCounts(before);
        TaskProposalEntity proposal =
                taskProposalRepository.findById(fixture.proposalId()).orElseThrow();
        assertEquals(TaskProposalStatus.PENDING, proposal.getStatus());
        assertNull(proposal.getReason());
        assertNull(proposal.getReviewedById());
    }

    @Test
    void preloadedStaleActorIsClearedBeforeProposalLockAndFreshStateDeniesReview() {
        Fixture fixture = fixture("stale-actor", false);
        Counts before = counts();
        AtomicBoolean staleActorWasManaged = new AtomicBoolean();

        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        assertThrows(AccessDeniedException.class, () -> outer.executeWithoutResult(status -> {
            User staleActor = userRepository.findById(fixture.actorId()).orElseThrow();
            staleActorWasManaged.set(entityManager.contains(staleActor));
            requiresNewTransaction().executeWithoutResult(innerStatus -> {
                User currentActor = userRepository.findById(fixture.actorId()).orElseThrow();
                currentActor.setActive(false);
                userRepository.saveAndFlush(currentActor);
            });
            authenticate(fixture.username());
            taskProposalService.approve(fixture.proposalId());
        }));

        assertTrue(staleActorWasManaged.get());
        assertCounts(before);
        assertEquals(TaskProposalStatus.PENDING,
                taskProposalRepository.findById(fixture.proposalId())
                        .orElseThrow().getStatus());
    }

    private Fixture fixture(String suffix, boolean managerReview) {
        Role studentRole = roleRepository.findByName("STUDENT")
                .orElseGet(() -> roleRepository.save(new Role("STUDENT", "Student")));
        Role managerRole = roleRepository.findByName("LAB_MANAGER")
                .orElseGet(() -> roleRepository.save(new Role(
                        "LAB_MANAGER", "Lab manager")));
        User actor = new User();
        actor.setUsername("proposal-reviewer-" + suffix);
        actor.setEmail("proposal-reviewer-" + suffix + "@example.test");
        actor.setPassword("password");
        actor.addRole(studentRole);
        if (managerReview) {
            actor.addRole(managerRole);
        }
        actor = userRepository.saveAndFlush(actor);

        Laboratory lab = new Laboratory();
        lab.setLabName("Proposal Review Lab " + suffix);
        lab.setLocation("Room");
        lab.setCapacity(10);
        lab.setManager(managerReview ? actor : null);
        lab = laboratoryRepository.saveAndFlush(lab);

        ProjectEntity project = ProjectEntity.builder()
                .lab(lab)
                .title("Proposal Review Project " + suffix)
                .build();
        project = projectRepository.saveAndFlush(project);

        GroupEntity group = GroupEntity.builder()
                .lab(lab)
                .project(project)
                .leader(actor)
                .name("Proposal Review Group " + suffix)
                .build();
        group = groupRepository.saveAndFlush(group);

        GroupMemberEntity membership = GroupMemberEntity.builder()
                .group(group)
                .user(actor)
                .role(GroupRole.LEADER)
                .build();
        groupMemberRepository.saveAndFlush(membership);

        TaskProposalEntity proposal = TaskProposalEntity.builder()
                .proposedById(actor.getId())
                .projectId(project.getId())
                .groupId(group.getId())
                .payloadJson(payload(project.getId(), group.getId()))
                .status(TaskProposalStatus.PENDING)
                .build();
        proposal = taskProposalRepository.saveAndFlush(proposal);
        authenticate(actor.getUsername());
        clearInvocations(
                userRepository,
                taskRepository,
                taskProposalRepository,
                auditLogService,
                proposalNotificationPort
        );
        return new Fixture(
                actor.getId(),
                actor.getUsername(),
                project.getId(),
                group.getId(),
                proposal.getId()
        );
    }

    private String payload(Long projectId, Long groupId) {
        return """
                {"projectId":%d,"groupId":%d,"milestoneId":null,
                 "parentTaskId":null,"title":"Proposal title",
                 "description":null,"priority":"MEDIUM","type":"TASK",
                 "dueDate":null}
                """.formatted(projectId, groupId);
    }

    private void authenticate(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, List.of()));
    }

    private TransactionTemplate requiresNewTransaction() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private Counts counts() {
        return new Counts(
                taskProposalRepository.count(),
                auditLogRepository.count(),
                taskRepository.count(),
                taskActivityRepository.count()
        );
    }

    private void assertCounts(Counts expected) {
        assertEquals(expected.proposals(), taskProposalRepository.count());
        assertEquals(expected.audits(), auditLogRepository.count());
        assertEquals(expected.tasks(), taskRepository.count());
        assertEquals(expected.activities(), taskActivityRepository.count());
    }

    private record Fixture(
            Long actorId,
            String username,
            Long projectId,
            Long groupId,
            Long proposalId
    ) {
    }

    private record Counts(
            long proposals,
            long audits,
            long tasks,
            long activities
    ) {
    }
}
