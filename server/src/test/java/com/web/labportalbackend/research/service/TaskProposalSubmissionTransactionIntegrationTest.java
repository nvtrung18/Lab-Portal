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
import com.web.labportalbackend.research.dto.request.CreateTaskProposalRequest;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.GroupMemberEntity;
import com.web.labportalbackend.research.entity.ProjectEntity;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

@SpringBootTest
class TaskProposalSubmissionTransactionIntegrationTest {

    @Autowired TaskProposalService taskProposalService;
    @Autowired RoleRepository roleRepository;
    @MockitoSpyBean UserRepository userRepository;
    @Autowired LaboratoryRepository laboratoryRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired GroupRepository groupRepository;
    @Autowired GroupMemberRepository groupMemberRepository;
    @MockitoSpyBean TaskProposalRepository taskProposalRepository;
    @MockitoSpyBean AuditLogRepository auditLogRepository;
    @Autowired TaskActivityRepository taskActivityRepository;
    @Autowired TaskRepository taskRepository;
    @MockitoSpyBean AuditLogService auditLogService;
    @Autowired PlatformTransactionManager transactionManager;
    @PersistenceContext EntityManager entityManager;

    @AfterEach
    void clearSecurityAndSpies() {
        SecurityContextHolder.clearContext();
        reset(userRepository, taskProposalRepository, auditLogRepository, auditLogService);
    }

    @Test
    void successCommitsOneProposalAndAuditWithNoTaskOrActivity() {
        Fixture fixture = fixture("success");
        Counts before = counts();

        var response = taskProposalService.submit(request(fixture));

        assertEquals(before.proposals() + 1, taskProposalRepository.count());
        assertEquals(before.audits() + 1, auditLogRepository.count());
        assertEquals(before.tasks(), taskRepository.count());
        assertEquals(before.activities(), taskActivityRepository.count());
        var proposal = taskProposalRepository.findById(response.getId()).orElseThrow();
        assertEquals(fixture.actorId(), proposal.getProposedById());
        var audit = auditLogRepository.findAll().stream()
                .filter(row -> row.getAction() == AuditAction.CREATE_TASK_PROPOSAL)
                .filter(row -> response.getId().equals(row.getTargetId()))
                .findFirst()
                .orElseThrow();
        assertEquals(fixture.actorId(), audit.getActorId());
        assertEquals("TASK_PROPOSAL", audit.getTargetType());
        assertEquals("Submitted task proposal", audit.getDescription());
    }

    @Test
    void failureAfterRealAuditPersistenceAttemptRollsBackProposalAndAudit() {
        Fixture fixture = fixture("audit-failure");
        Counts before = counts();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new RuntimeException("failure after audit persistence attempt");
        })
                .when(auditLogService)
                .log(any(), any(), any(), any(), any(), any());

        assertThrows(RuntimeException.class, () -> taskProposalService.submit(request(fixture)));

        verify(auditLogRepository).save(any());
        assertCounts(before);
    }

    @Test
    void proposalSaveFailureWritesNoAudit() {
        Fixture fixture = fixture("save-failure");
        Counts before = counts();
        doThrow(new RuntimeException("proposal save failed"))
                .when(taskProposalRepository)
                .saveAndFlush(any());

        assertThrows(RuntimeException.class, () -> taskProposalService.submit(request(fixture)));

        assertCounts(before);
        verify(auditLogService, never()).log(any(), any(), any(), any(), any(), any());
    }

    @Test
    void identicalSubmissionsCreateDistinctProposalAndAuditRows() {
        Fixture fixture = fixture("duplicates");
        Counts before = counts();
        CreateTaskProposalRequest request = request(fixture);

        var first = taskProposalService.submit(request);
        var second = taskProposalService.submit(request);

        assertFalse(first.getId().equals(second.getId()));
        assertEquals(before.proposals() + 2, taskProposalRepository.count());
        assertEquals(before.audits() + 2, auditLogRepository.count());
        assertEquals(before.tasks(), taskRepository.count());
        assertEquals(before.activities(), taskActivityRepository.count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"inactive", "deleted"})
    void unusableSharedLaboratoryRejectsWithNoWrites(String state) {
        Fixture fixture = fixture("lab-" + state);
        TransactionTemplate transaction = requiresNewTransaction();
        transaction.executeWithoutResult(status -> {
            Laboratory lab = laboratoryRepository.findById(fixture.labId()).orElseThrow();
            if ("inactive".equals(state)) {
                lab.setActive(false);
            } else {
                lab.setDeleted(true);
            }
            laboratoryRepository.saveAndFlush(lab);
        });
        Counts before = counts();

        assertThrows(RuntimeException.class, () -> taskProposalService.submit(request(fixture)));

        assertCounts(before);
    }

    @Test
    void softStateChangeCommittedBeforeFinalLabLockIsObservedAndRejected() {
        Fixture fixture = fixture("pre-lock");
        Counts before = counts();
        AtomicBoolean changed = new AtomicBoolean();
        TransactionTemplate transaction = requiresNewTransaction();
        doAnswer(invocation -> {
            if (changed.compareAndSet(false, true)) {
                transaction.executeWithoutResult(status -> {
                    Laboratory lab = laboratoryRepository.findById(fixture.labId()).orElseThrow();
                    lab.setActive(false);
                    laboratoryRepository.saveAndFlush(lab);
                });
            }
            return invocation.callRealMethod();
        }).when(userRepository).findByIdForStatusAuthorization(fixture.actorId());

        assertThrows(RuntimeException.class, () -> taskProposalService.submit(request(fixture)));

        assertTrue(changed.get());
        assertCounts(before);
    }

    @Test
    void updateAfterFinalLocksCannotCommitUntilSubmissionTransactionCompletes() throws Exception {
        Fixture fixture = fixture("post-lock");
        Counts before = counts();
        CountDownLatch finalLocksHeld = new CountDownLatch(1);
        CountDownLatch allowProposalSave = new CountDownLatch(1);
        CountDownLatch competingMutationReachedFlush = new CountDownLatch(1);
        doAnswer(invocation -> {
            finalLocksHeld.countDown();
            if (!allowProposalSave.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting to release proposal transaction");
            }
            var proposal = invocation.getArgument(
                    0,
                    com.web.labportalbackend.research.entity.TaskProposalEntity.class
            );
            entityManager.persist(proposal);
            entityManager.flush();
            return proposal;
        }).when(taskProposalRepository).saveAndFlush(any());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Long> submission = executor.submit(() -> {
                authenticate(fixture.username());
                try {
                    return taskProposalService.submit(request(fixture)).getId();
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });
            assertTrue(finalLocksHeld.await(5, TimeUnit.SECONDS));

            Future<Void> update = executor.submit(() -> {
                requiresNewTransaction().executeWithoutResult(status -> {
                    Laboratory lab = laboratoryRepository.findById(fixture.labId()).orElseThrow();
                    lab.setActive(false);
                    competingMutationReachedFlush.countDown();
                    laboratoryRepository.saveAndFlush(lab);
                });
                return null;
            });

            assertTrue(competingMutationReachedFlush.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> update.get(300, TimeUnit.MILLISECONDS));
            allowProposalSave.countDown();
            Long proposalId = submission.get(5, TimeUnit.SECONDS);
            update.get(5, TimeUnit.SECONDS);

            assertTrue(taskProposalRepository.findById(proposalId).isPresent());
            assertFalse(laboratoryRepository.findById(fixture.labId()).orElseThrow().getActive());
            assertEquals(before.proposals() + 1, taskProposalRepository.count());
            assertEquals(before.audits() + 1, auditLogRepository.count());
            assertEquals(before.tasks(), taskRepository.count());
            assertEquals(before.activities(), taskActivityRepository.count());
        } finally {
            allowProposalSave.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    private Fixture fixture(String suffix) {
        Role studentRole = roleRepository.findByName("STUDENT")
                .orElseGet(() -> roleRepository.save(new Role("STUDENT", "Student")));
        User actor = new User();
        actor.setUsername("proposal-student-" + suffix);
        actor.setEmail("proposal-student-" + suffix + "@example.test");
        actor.setPassword("password");
        actor.setActive(true);
        actor.setDeleted(false);
        actor.addRole(studentRole);
        actor = userRepository.save(actor);

        Laboratory lab = new Laboratory();
        lab.setLabName("Proposal Lab " + suffix);
        lab.setLocation("Room");
        lab.setCapacity(10);
        lab.setManager(actor);
        lab = laboratoryRepository.save(lab);

        ProjectEntity project = ProjectEntity.builder()
                .lab(lab)
                .title("Proposal Project " + suffix)
                .build();
        project = projectRepository.save(project);

        GroupEntity group = GroupEntity.builder()
                .lab(lab)
                .project(project)
                .leader(actor)
                .name("Proposal Group " + suffix)
                .build();
        group = groupRepository.save(group);

        GroupMemberEntity membership = GroupMemberEntity.builder()
                .group(group)
                .user(actor)
                .role(com.web.labportalbackend.research.enums.GroupRole.MEMBER)
                .build();
        groupMemberRepository.save(membership);
        authenticate(actor.getUsername());
        clearInvocations(userRepository, taskProposalRepository, auditLogService);
        return new Fixture(
                actor.getId(),
                actor.getUsername(),
                lab.getId(),
                project.getId(),
                group.getId()
        );
    }

    private CreateTaskProposalRequest request(Fixture fixture) {
        CreateTaskProposalRequest request = new CreateTaskProposalRequest();
        request.setProjectId(fixture.projectId());
        request.setGroupId(fixture.groupId());
        request.setTitle("Proposal " + fixture.username());
        return request;
    }

    private void authenticate(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, List.of()));
    }

    private TransactionTemplate requiresNewTransaction() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
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
            Long labId,
            Long projectId,
            Long groupId
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
