package com.web.labportalbackend.research.service;

import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.RoleRepository;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.dto.request.RejectTaskProposalRequest;
import com.web.labportalbackend.research.dto.response.TaskProposalReviewResponse;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.GroupMemberEntity;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.entity.TaskProposalEntity;
import com.web.labportalbackend.research.enums.GroupRole;
import com.web.labportalbackend.research.enums.TaskProposalStatus;
import com.web.labportalbackend.research.exception.TaskProposalReviewConflictException;
import com.web.labportalbackend.research.repository.GroupMemberRepository;
import com.web.labportalbackend.research.repository.GroupRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.repository.TaskProposalRepository;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Opt-in MySQL/InnoDB evidence for native proposal-review serialization. */
@SpringBootTest
@Import(TaskProposalReviewMySqlIntegrationTest.LockProbeConfiguration.class)
@EnabledIfEnvironmentVariable(named = "LAB_PORTAL_MYSQL_IT", matches = "(?i:true)")
class TaskProposalReviewMySqlIntegrationTest {

    private static final String DEFAULT_URL =
            "jdbc:mysql://127.0.0.1:3307/lab_portal_it";
    private static final AtomicLong SEQUENCE =
            new AtomicLong(System.currentTimeMillis());

    @Autowired TaskProposalService taskProposalService;
    @Autowired RoleRepository roleRepository;
    @Autowired UserRepository userRepository;
    @Autowired LaboratoryRepository laboratoryRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired GroupRepository groupRepository;
    @Autowired GroupMemberRepository groupMemberRepository;
    @Autowired TaskProposalRepository taskProposalRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired LockProbe lockProbe;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        String url = environmentOrDefault(
                "LAB_PORTAL_MYSQL_IT_URL", DEFAULT_URL);
        assertDisposableDatabase(url);
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username",
                () -> requiredEnvironment("LAB_PORTAL_MYSQL_IT_USERNAME"));
        registry.add("spring.datasource.password",
                () -> requiredEnvironment("LAB_PORTAL_MYSQL_IT_PASSWORD"));
        registry.add("spring.datasource.driver-class-name",
                () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.baseline-on-migrate", () -> "false");
        registry.add("spring.flyway.clean-disabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.MySQLDialect");
    }

    @AfterEach
    void clearSecurityAndProbe() {
        SecurityContextHolder.clearContext();
        lockProbe.clear();
    }

    @Test
    void concurrentApprovalsCreateExactlyOneOfficialTask() throws Exception {
        Fixture fixture = fixture("approve-approve");
        try {
            List<Attempt> attempts = runWithFirstReviewLockHeld(
                    fixture,
                    () -> taskProposalService.approve(fixture.proposalId()),
                    () -> taskProposalService.approve(fixture.proposalId())
            );

            assertOneSuccessAndOneConflict(attempts);
            assertEquals(TaskProposalStatus.APPROVED,
                    taskProposalRepository.findById(fixture.proposalId())
                            .orElseThrow().getStatus());
            assertEquals(1, taskCount(fixture));
            assertEquals(1, reviewAuditCount(fixture));
            assertEquals(0, taskActivityCount(fixture));
        } finally {
            cleanup(fixture);
        }
    }

    @Test
    void concurrentApproveRejectCommitsOneTerminalDecisionAndMatchingTaskCount()
            throws Exception {
        Fixture fixture = fixture("approve-reject");
        try {
            RejectTaskProposalRequest reject = new RejectTaskProposalRequest();
            reject.setReason("Native concurrency rejection");
            List<Attempt> attempts = runWithFirstReviewLockHeld(
                    fixture,
                    () -> taskProposalService.approve(fixture.proposalId()),
                    () -> taskProposalService.reject(fixture.proposalId(), reject)
            );

            assertOneSuccessAndOneConflict(attempts);
            assertEquals(TaskProposalStatus.APPROVED,
                    taskProposalRepository.findById(fixture.proposalId())
                            .orElseThrow().getStatus());
            assertEquals(1, taskCount(fixture));
            assertEquals(1, reviewAuditCount(fixture));
            assertEquals(0, taskActivityCount(fixture));
        } finally {
            cleanup(fixture);
        }
    }

    @Test
    void leaderMembershipReadLockBlocksRoleMutationUntilReviewCommits()
            throws Exception {
        Fixture fixture = fixture("membership-lock");
        CountDownLatch authorizationLockAcquired = new CountDownLatch(1);
        CountDownLatch releaseReview = new CountDownLatch(1);
        CountDownLatch mutationDatabaseCallEntered = new CountDownLatch(1);
        AtomicLong reviewConnectionId = new AtomicLong(-1);
        AtomicLong mutationConnectionId = new AtomicLong(-1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        lockProbe.holdMembershipAuthorization(
                fixture.groupId(),
                fixture.actorId(),
                authorizationLockAcquired,
                releaseReview,
                reviewConnectionId
        );

        try {
            Future<Attempt> reviewFuture = executor.submit(() ->
                    attempt(
                            fixture.username(),
                            () -> taskProposalService.approve(fixture.proposalId())));
            assertTrue(authorizationLockAcquired.await(10, TimeUnit.SECONDS),
                    "Review did not acquire the leader membership lock");

            Future<Integer> mutationFuture = executor.submit(() ->
                    requiresNewTransaction().execute(status -> {
                        mutationConnectionId.set(
                                lockProbe.captureTransactionConnectionId());
                        mutationDatabaseCallEntered.countDown();
                        return jdbcTemplate.update(
                                """
                                UPDATE group_members
                                SET role = 'MEMBER'
                                WHERE id = ?
                                """,
                                fixture.membershipId()
                        );
                    }));
            assertTrue(mutationDatabaseCallEntered.await(10, TimeUnit.SECONDS),
                    "Competing membership mutation did not reach its database call");
            assertCapturedConnectionIds(
                    mutationConnectionId.get(),
                    reviewConnectionId.get());
            try {
                LockWaitEvidence evidence = awaitCausalDataLockWait(
                        mutationConnectionId.get(),
                        reviewConnectionId.get(),
                        "group_members",
                        fixture.membershipId()
                );
                assertEquals(mutationConnectionId.get(),
                        evidence.requestingConnectionId());
                assertEquals(reviewConnectionId.get(),
                        evidence.blockingConnectionId());
                assertTrue(evidence.requestingLockMode().startsWith("X"));
                assertTrue(evidence.blockingLockMode().startsWith("S"));
                assertFalse(reviewFuture.isDone(),
                        "Review completed before its explicit release");
                assertFalse(mutationFuture.isDone(),
                        "Membership mutation completed while review still held its lock");
            } finally {
                releaseReview.countDown();
            }

            assertTrue(reviewFuture.get(20, TimeUnit.SECONDS).success());
            assertEquals(1, mutationFuture.get(20, TimeUnit.SECONDS));
            assertCausalDataLockWaitGone(
                    mutationConnectionId.get(),
                    reviewConnectionId.get(),
                    "group_members",
                    fixture.membershipId()
            );
            TaskProposalEntity proposal =
                    taskProposalRepository.findById(fixture.proposalId()).orElseThrow();
            assertEquals(TaskProposalStatus.APPROVED, proposal.getStatus());
            assertEquals(fixture.actorId(), proposal.getReviewedById());
            assertEquals("MEMBER", jdbcTemplate.queryForObject(
                    "SELECT role FROM group_members WHERE id = ?",
                    String.class,
                    fixture.membershipId()
            ));
            assertEquals(1, taskCount(fixture));
            assertEquals(1, reviewAuditCount(fixture));
            assertEquals(0, taskActivityCount(fixture));
        } finally {
            releaseReview.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
            cleanup(fixture);
        }
    }

    private List<Attempt> runWithFirstReviewLockHeld(
            Fixture fixture,
            ReviewCall first,
            ReviewCall second
    ) throws Exception {
        CountDownLatch firstLockAcquired = new CountDownLatch(1);
        CountDownLatch releaseFirstReview = new CountDownLatch(1);
        CountDownLatch secondDatabaseCallEntered = new CountDownLatch(1);
        AtomicBoolean firstInvocation = new AtomicBoolean(true);
        AtomicLong firstConnectionId = new AtomicLong(-1);
        AtomicLong secondConnectionId = new AtomicLong(-1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        lockProbe.holdFirstProposalReview(
                fixture.proposalId(),
                firstInvocation,
                firstLockAcquired,
                releaseFirstReview,
                secondDatabaseCallEntered,
                firstConnectionId,
                secondConnectionId
        );

        try {
            Future<Attempt> firstFuture = executor.submit(
                    () -> attempt(fixture.username(), first));
            assertTrue(firstLockAcquired.await(10, TimeUnit.SECONDS),
                    "First review did not acquire the proposal row lock");
            Future<Attempt> secondFuture = executor.submit(
                    () -> attempt(fixture.username(), second));
            assertTrue(secondDatabaseCallEntered.await(10, TimeUnit.SECONDS),
                    "Second review did not reach findByIdForReview");
            assertCapturedConnectionIds(
                    secondConnectionId.get(),
                    firstConnectionId.get());
            try {
                LockWaitEvidence evidence = awaitCausalDataLockWait(
                        secondConnectionId.get(),
                        firstConnectionId.get(),
                        "research_task_proposal",
                        fixture.proposalId()
                );
                assertEquals(secondConnectionId.get(),
                        evidence.requestingConnectionId());
                assertEquals(firstConnectionId.get(),
                        evidence.blockingConnectionId());
                assertTrue(evidence.requestingLockMode().startsWith("X"));
                assertTrue(evidence.blockingLockMode().startsWith("X"));
                assertFalse(firstFuture.isDone(),
                        "First review completed before its explicit release");
                assertFalse(secondFuture.isDone(),
                        "Second review completed before the first lock was released");
            } finally {
                releaseFirstReview.countDown();
            }

            Attempt firstAttempt = firstFuture.get(20, TimeUnit.SECONDS);
            Attempt secondAttempt = secondFuture.get(20, TimeUnit.SECONDS);
            assertCausalDataLockWaitGone(
                    secondConnectionId.get(),
                    firstConnectionId.get(),
                    "research_task_proposal",
                    fixture.proposalId()
            );
            return List.of(firstAttempt, secondAttempt);
        } finally {
            releaseFirstReview.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private Attempt attempt(
            String username,
            ReviewCall call
    ) {
        authenticate(username);
        try {
            TaskProposalReviewResponse response = call.execute();
            return new Attempt(true, response.getStatus());
        } catch (TaskProposalReviewConflictException ex) {
            return new Attempt(false, null);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void assertOneSuccessAndOneConflict(List<Attempt> attempts) {
        assertEquals(1, attempts.stream().filter(Attempt::success).count());
        assertEquals(1, attempts.stream().filter(attempt -> !attempt.success()).count());
    }

    private void assertCapturedConnectionIds(
            long requestingConnectionId,
            long blockingConnectionId
    ) {
        assertTrue(requestingConnectionId > 0,
                "The requesting transaction did not capture CONNECTION_ID()");
        assertTrue(blockingConnectionId > 0,
                "The blocking transaction did not capture CONNECTION_ID()");
        assertFalse(requestingConnectionId == blockingConnectionId,
                "Blocking and requesting transactions used the same MySQL connection");
    }

    private LockWaitEvidence awaitCausalDataLockWait(
            long requestingConnectionId,
            long blockingConnectionId,
            String table,
            Long recordId
    ) {
        String schema = disposableSchema();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        do {
            List<LockWaitEvidence> waits = causalDataLockWaits(
                    requestingConnectionId,
                    blockingConnectionId,
                    schema,
                    table,
                    recordId
            );
            if (!waits.isEmpty()) {
                assertEquals(1, waits.size(),
                        "Expected one exact row-lock wait relationship");
                LockWaitEvidence evidence = waits.getFirst();
                assertEquals(schema, evidence.schema());
                assertEquals(table, evidence.table());
                assertEquals("PRIMARY", evidence.index());
                assertEquals("RECORD", evidence.lockType());
                assertEquals(recordId.toString(), evidence.requestingLockData());
                assertEquals(recordId.toString(), evidence.blockingLockData());
                assertTrue(evidence.requestingThreadId() > 0,
                        "Requesting Performance Schema thread was not exposed");
                assertTrue(evidence.blockingThreadId() > 0,
                        "Blocking Performance Schema thread was not exposed");
                assertTrue(evidence.requestingTransactionId() != null
                                && !evidence.requestingTransactionId().isBlank(),
                        "Requesting MySQL transaction identity was not exposed");
                assertTrue(evidence.blockingTransactionId() != null
                                && !evidence.blockingTransactionId().isBlank(),
                        "Blocking MySQL transaction identity was not exposed");
                return evidence;
            }
            Thread.onSpinWait();
        } while (System.nanoTime() < deadline);
        return fail("The exact requesting/blocking transactions never appeared "
                + "as a physical InnoDB waiter on " + schema + "." + table
                + " PRIMARY record " + recordId);
    }

    private void assertCausalDataLockWaitGone(
            long requestingConnectionId,
            long blockingConnectionId,
            String table,
            Long recordId
    ) {
        assertTrue(causalDataLockWaits(
                        requestingConnectionId,
                        blockingConnectionId,
                        disposableSchema(),
                        table,
                        recordId
                ).isEmpty(),
                "The exact data-lock wait remained after explicit release");
    }

    private List<LockWaitEvidence> causalDataLockWaits(
            long requestingConnectionId,
            long blockingConnectionId,
            String schema,
            String table,
            Long recordId
    ) {
        return jdbcTemplate.query(
                """
                SELECT requesting_thread.PROCESSLIST_ID,
                       waits.REQUESTING_THREAD_ID,
                       COALESCE(CAST(requesting_transaction.TRX_ID AS CHAR),
                                CAST(waits.REQUESTING_ENGINE_TRANSACTION_ID AS CHAR)),
                       blocking_thread.PROCESSLIST_ID,
                       waits.BLOCKING_THREAD_ID,
                       COALESCE(CAST(blocking_transaction.TRX_ID AS CHAR),
                                CAST(waits.BLOCKING_ENGINE_TRANSACTION_ID AS CHAR)),
                       requested_lock.OBJECT_SCHEMA,
                       requested_lock.OBJECT_NAME,
                       requested_lock.INDEX_NAME,
                       requested_lock.LOCK_TYPE,
                       requested_lock.LOCK_MODE,
                       requested_lock.LOCK_DATA,
                       blocking_lock.LOCK_MODE,
                       blocking_lock.LOCK_DATA
                FROM performance_schema.data_lock_waits waits
                JOIN performance_schema.data_locks requested_lock
                  ON requested_lock.ENGINE = waits.ENGINE
                 AND requested_lock.ENGINE_LOCK_ID =
                     waits.REQUESTING_ENGINE_LOCK_ID
                 AND requested_lock.THREAD_ID = waits.REQUESTING_THREAD_ID
                JOIN performance_schema.data_locks blocking_lock
                  ON blocking_lock.ENGINE = waits.ENGINE
                 AND blocking_lock.ENGINE_LOCK_ID =
                     waits.BLOCKING_ENGINE_LOCK_ID
                 AND blocking_lock.THREAD_ID = waits.BLOCKING_THREAD_ID
                JOIN performance_schema.threads requesting_thread
                  ON requesting_thread.THREAD_ID = waits.REQUESTING_THREAD_ID
                JOIN performance_schema.threads blocking_thread
                  ON blocking_thread.THREAD_ID = waits.BLOCKING_THREAD_ID
                LEFT JOIN performance_schema.events_transactions_current
                          requesting_transaction
                  ON requesting_transaction.THREAD_ID =
                     waits.REQUESTING_THREAD_ID
                 AND requesting_transaction.STATE = 'ACTIVE'
                LEFT JOIN performance_schema.events_transactions_current
                          blocking_transaction
                  ON blocking_transaction.THREAD_ID =
                     waits.BLOCKING_THREAD_ID
                 AND blocking_transaction.STATE = 'ACTIVE'
                WHERE requesting_thread.PROCESSLIST_ID = ?
                  AND blocking_thread.PROCESSLIST_ID = ?
                  AND requested_lock.OBJECT_SCHEMA = ?
                  AND blocking_lock.OBJECT_SCHEMA = ?
                  AND requested_lock.OBJECT_NAME = ?
                  AND blocking_lock.OBJECT_NAME = ?
                  AND requested_lock.INDEX_NAME = 'PRIMARY'
                  AND blocking_lock.INDEX_NAME = 'PRIMARY'
                  AND requested_lock.LOCK_TYPE = 'RECORD'
                  AND blocking_lock.LOCK_TYPE = 'RECORD'
                  AND requested_lock.LOCK_DATA = ?
                  AND blocking_lock.LOCK_DATA = ?
                """,
                (resultSet, rowNumber) -> new LockWaitEvidence(
                        resultSet.getLong(1),
                        resultSet.getLong(2),
                        resultSet.getString(3),
                        resultSet.getLong(4),
                        resultSet.getLong(5),
                        resultSet.getString(6),
                        resultSet.getString(7),
                        resultSet.getString(8),
                        resultSet.getString(9),
                        resultSet.getString(10),
                        resultSet.getString(11),
                        resultSet.getString(12),
                        resultSet.getString(13),
                        resultSet.getString(14)
                ),
                requestingConnectionId,
                blockingConnectionId,
                schema,
                schema,
                table,
                table,
                recordId.toString(),
                recordId.toString()
        );
    }

    private String disposableSchema() {
        String schema = jdbcTemplate.queryForObject(
                "SELECT DATABASE()", String.class);
        assertTrue(schema != null
                        && schema.toLowerCase(Locale.ROOT).endsWith("_it"),
                "Native lock evidence must use the disposable _it schema");
        return schema;
    }

    private TransactionTemplate requiresNewTransaction() {
        TransactionTemplate template =
                new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private Fixture fixture(String label) {
        String token = "proposal-review-mysql-" + label + "-"
                + SEQUENCE.incrementAndGet();
        Role studentRole = roleRepository.findByName("STUDENT")
                .orElseThrow();
        User actor = new User();
        actor.setUsername(trimTo(token, 50));
        actor.setEmail(trimTo(token, 70) + "@mysql-it.example.test");
        actor.setPassword("not-a-real-credential");
        actor.addRole(studentRole);
        actor = userRepository.saveAndFlush(actor);

        Laboratory lab = new Laboratory();
        lab.setLabName(trimTo(token + "-lab", 100));
        lab.setLocation("MySQL proposal review integration test");
        lab.setCapacity(1);
        lab = laboratoryRepository.saveAndFlush(lab);

        ProjectEntity project = projectRepository.saveAndFlush(
                ProjectEntity.builder()
                        .lab(lab)
                        .title(trimTo(token + " project", 200))
                        .build()
        );
        GroupEntity group = groupRepository.saveAndFlush(
                GroupEntity.builder()
                        .lab(lab)
                        .project(project)
                        .leader(actor)
                        .name(trimTo(token + " group", 150))
                        .build()
        );
        GroupMemberEntity membership = groupMemberRepository.saveAndFlush(
                GroupMemberEntity.builder()
                        .group(group)
                        .user(actor)
                        .role(GroupRole.LEADER)
                        .build()
        );
        TaskProposalEntity proposal = taskProposalRepository.saveAndFlush(
                TaskProposalEntity.builder()
                        .proposedById(actor.getId())
                        .projectId(project.getId())
                        .groupId(group.getId())
                        .payloadJson(payload(project.getId(), group.getId()))
                        .status(TaskProposalStatus.PENDING)
                        .build()
        );
        return new Fixture(
                actor.getId(),
                actor.getUsername(),
                lab.getId(),
                project.getId(),
                group.getId(),
                membership.getId(),
                proposal.getId()
        );
    }

    private int taskCount(Fixture fixture) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tasks WHERE project_id = ? AND group_id = ?",
                Integer.class,
                fixture.projectId(),
                fixture.groupId()
        );
    }

    private int reviewAuditCount(Fixture fixture) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM audit_logs
                WHERE action = 'REVIEW_TASK_PROPOSAL'
                  AND target_type = 'TASK_PROPOSAL'
                  AND target_id = ?
                """,
                Integer.class,
                fixture.proposalId()
        );
    }

    private int taskActivityCount(Fixture fixture) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM research_task_activity a
                JOIN tasks t ON t.id = a.task_id
                WHERE t.project_id = ? AND t.group_id = ?
                """,
                Integer.class,
                fixture.projectId(),
                fixture.groupId()
        );
    }

    private void cleanup(Fixture fixture) {
        jdbcTemplate.update(
                "DELETE FROM audit_logs WHERE target_type = 'TASK_PROPOSAL' AND target_id = ?",
                fixture.proposalId()
        );
        jdbcTemplate.update(
                "DELETE FROM research_task_activity WHERE task_id IN (SELECT id FROM tasks WHERE project_id = ? AND group_id = ?)",
                fixture.projectId(),
                fixture.groupId()
        );
        jdbcTemplate.update(
                "DELETE FROM tasks WHERE project_id = ? AND group_id = ?",
                fixture.projectId(),
                fixture.groupId()
        );
        jdbcTemplate.update(
                "DELETE FROM research_task_proposal WHERE id = ?",
                fixture.proposalId()
        );
        jdbcTemplate.update(
                "DELETE FROM group_members WHERE group_id = ?",
                fixture.groupId()
        );
        jdbcTemplate.update(
                "DELETE FROM research_groups WHERE id = ?",
                fixture.groupId()
        );
        jdbcTemplate.update(
                "DELETE FROM projects WHERE id = ?",
                fixture.projectId()
        );
        jdbcTemplate.update(
                "DELETE FROM laboratories WHERE id = ?",
                fixture.labId()
        );
        jdbcTemplate.update(
                "DELETE FROM user_roles WHERE user_id = ?",
                fixture.actorId()
        );
        jdbcTemplate.update(
                "DELETE FROM users WHERE id = ?",
                fixture.actorId()
        );
    }

    private void authenticate(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, List.of()));
    }

    private static String payload(Long projectId, Long groupId) {
        return """
                {"projectId":%d,"groupId":%d,"milestoneId":null,
                 "parentTaskId":null,"title":"Proposal title",
                 "description":null,"priority":"MEDIUM","type":"TASK",
                 "dueDate":null}
                """.formatted(projectId, groupId);
    }

    private static String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    name + " must be set for the opt-in MySQL integration suite");
        }
        return value;
    }

    private static void assertDisposableDatabase(String url) {
        int slash = url.lastIndexOf('/');
        int query = url.indexOf('?', slash);
        String database = slash < 0
                ? ""
                : url.substring(slash + 1, query < 0 ? url.length() : query);
        if (!database.toLowerCase(Locale.ROOT).endsWith("_it")) {
            throw new IllegalStateException(
                    "LAB_PORTAL_MYSQL_IT_URL must target a disposable database ending in _it");
        }
    }

    private static String trimTo(String value, int maxLength) {
        return value.length() <= maxLength
                ? value
                : value.substring(0, maxLength);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class LockProbeConfiguration {

        @Bean
        LockProbe lockProbe(JdbcTemplate jdbcTemplate) {
            return new LockProbe(jdbcTemplate);
        }

        @Bean
        LockProbeAspect lockProbeAspect(LockProbe lockProbe) {
            return new LockProbeAspect(lockProbe);
        }
    }

    @Aspect
    static class LockProbeAspect {

        private final LockProbe lockProbe;

        LockProbeAspect(LockProbe lockProbe) {
            this.lockProbe = lockProbe;
        }

        @Around("execution(* com.web.labportalbackend.research.repository.TaskProposalRepository.findByIdForReview(..))")
        Object aroundProposalReview(ProceedingJoinPoint joinPoint) throws Throwable {
            return lockProbe.aroundProposalReview(
                    joinPoint,
                    (Long) joinPoint.getArgs()[0]
            );
        }

        @Around("execution(* com.web.labportalbackend.research.repository.GroupMemberRepository.findActiveForStatusAuthorization(..))")
        Object aroundMembershipAuthorization(ProceedingJoinPoint joinPoint)
                throws Throwable {
            return lockProbe.aroundMembershipAuthorization(
                    joinPoint,
                    (Long) joinPoint.getArgs()[0],
                    (Long) joinPoint.getArgs()[1]
            );
        }
    }

    static class LockProbe {

        private final JdbcTemplate jdbcTemplate;
        private volatile ProposalHold proposalHold;
        private volatile MembershipHold membershipHold;

        LockProbe(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        void holdFirstProposalReview(
                Long proposalId,
                AtomicBoolean firstInvocation,
                CountDownLatch firstLockAcquired,
                CountDownLatch releaseFirstReview,
                CountDownLatch secondDatabaseCallEntered,
                AtomicLong firstConnectionId,
                AtomicLong secondConnectionId
        ) {
            proposalHold = new ProposalHold(
                    proposalId,
                    firstInvocation,
                    firstLockAcquired,
                    releaseFirstReview,
                    secondDatabaseCallEntered,
                    firstConnectionId,
                    secondConnectionId
            );
        }

        void holdMembershipAuthorization(
                Long groupId,
                Long actorId,
                CountDownLatch authorizationLockAcquired,
                CountDownLatch releaseReview,
                AtomicLong reviewConnectionId
        ) {
            membershipHold = new MembershipHold(
                    groupId,
                    actorId,
                    authorizationLockAcquired,
                    releaseReview,
                    reviewConnectionId
            );
        }

        Object aroundProposalReview(
                ProceedingJoinPoint joinPoint,
                Long proposalId
        ) throws Throwable {
            ProposalHold hold = proposalHold;
            if (hold == null || !hold.proposalId().equals(proposalId)) {
                return joinPoint.proceed();
            }
            if (hold.firstInvocation().compareAndSet(true, false)) {
                Object result = joinPoint.proceed();
                hold.firstConnectionId().set(captureTransactionConnectionId());
                hold.firstLockAcquired().countDown();
                hold.releaseFirstReview().await();
                return result;
            }
            hold.secondConnectionId().set(captureTransactionConnectionId());
            hold.secondDatabaseCallEntered().countDown();
            return joinPoint.proceed();
        }

        Object aroundMembershipAuthorization(
                ProceedingJoinPoint joinPoint,
                Long groupId,
                Long actorId
        ) throws Throwable {
            MembershipHold hold = membershipHold;
            if (hold == null
                    || !hold.groupId().equals(groupId)
                    || !hold.actorId().equals(actorId)) {
                return joinPoint.proceed();
            }
            Object result = joinPoint.proceed();
            hold.reviewConnectionId().set(captureTransactionConnectionId());
            hold.authorizationLockAcquired().countDown();
            hold.releaseReview().await();
            return result;
        }

        long captureTransactionConnectionId() {
            if (!TransactionSynchronizationManager.isActualTransactionActive()) {
                throw new AssertionError(
                        "CONNECTION_ID() must be captured inside the tested transaction");
            }
            Long connectionId = jdbcTemplate.queryForObject(
                    "SELECT CONNECTION_ID()", Long.class);
            if (connectionId == null || connectionId <= 0) {
                throw new AssertionError(
                        "MySQL did not expose the transaction-bound connection identity");
            }
            return connectionId;
        }

        void clear() {
            proposalHold = null;
            membershipHold = null;
        }
    }

    private record ProposalHold(
            Long proposalId,
            AtomicBoolean firstInvocation,
            CountDownLatch firstLockAcquired,
            CountDownLatch releaseFirstReview,
            CountDownLatch secondDatabaseCallEntered,
            AtomicLong firstConnectionId,
            AtomicLong secondConnectionId
    ) {
    }

    private record MembershipHold(
            Long groupId,
            Long actorId,
            CountDownLatch authorizationLockAcquired,
            CountDownLatch releaseReview,
            AtomicLong reviewConnectionId
    ) {
    }

    private record LockWaitEvidence(
            long requestingConnectionId,
            long requestingThreadId,
            String requestingTransactionId,
            long blockingConnectionId,
            long blockingThreadId,
            String blockingTransactionId,
            String schema,
            String table,
            String index,
            String lockType,
            String requestingLockMode,
            String requestingLockData,
            String blockingLockMode,
            String blockingLockData
    ) {
    }

    @FunctionalInterface
    private interface ReviewCall {
        TaskProposalReviewResponse execute();
    }

    private record Attempt(boolean success, TaskProposalStatus status) {
    }

    private record Fixture(
            Long actorId,
            String username,
            Long labId,
            Long projectId,
            Long groupId,
            Long membershipId,
            Long proposalId
    ) {
    }
}
