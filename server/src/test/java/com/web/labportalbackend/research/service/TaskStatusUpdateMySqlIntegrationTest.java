package com.web.labportalbackend.research.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.labportalbackend.admin.systemconfig.dto.SystemConfigResponse;
import com.web.labportalbackend.admin.systemconfig.entity.SystemConfigEntity;
import com.web.labportalbackend.admin.systemconfig.repository.SystemConfigRepository;
import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.RoleRepository;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.dto.request.PatchTaskStatusRequest;
import com.web.labportalbackend.research.dto.response.TaskResponse;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.GroupMemberEntity;
import com.web.labportalbackend.research.entity.MilestoneEntity;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.entity.ReportEntity;
import com.web.labportalbackend.research.entity.TaskEntity;
import com.web.labportalbackend.research.enums.GroupRole;
import com.web.labportalbackend.research.enums.ReportStatus;
import com.web.labportalbackend.research.enums.TaskPriority;
import com.web.labportalbackend.research.enums.TaskStatus;
import com.web.labportalbackend.research.enums.TaskType;
import com.web.labportalbackend.research.repository.GroupMemberRepository;
import com.web.labportalbackend.research.repository.GroupRepository;
import com.web.labportalbackend.research.repository.MilestoneRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.repository.ReportRepository;
import com.web.labportalbackend.research.repository.TaskRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opt-in MySQL 8/InnoDB evidence for the canonical task-status authorization
 * transaction. The test deliberately uses the production repositories and
 * service proxy without repository/helper spies.
 *
 * <p>Start {@code docker/mysql-it-compose.yml}, then run this class with
 * {@code LAB_PORTAL_MYSQL_IT=true}. The URL safeguard accepts only a database
 * whose name ends in {@code _it}.</p>
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "LAB_PORTAL_MYSQL_IT", matches = "(?i:true)")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class TaskStatusUpdateMySqlIntegrationTest {

    private static final String DEFAULT_URL =
            "jdbc:mysql://127.0.0.1:3307/lab_portal_it"
                    + "?useUnicode=true&characterEncoding=utf8"
                    + "&connectionCollation=utf8mb4_unicode_ci"
                    + "&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true";
    private static final Duration LOCK_WAIT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration SERVICE_TIMEOUT = Duration.ofSeconds(20);
    private static final AtomicLong SEQUENCE = new AtomicLong(System.currentTimeMillis());

    @Autowired TaskService taskService;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired LaboratoryRepository laboratoryRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired GroupRepository groupRepository;
    @Autowired GroupMemberRepository groupMemberRepository;
    @Autowired MilestoneRepository milestoneRepository;
    @Autowired ReportRepository reportRepository;
    @Autowired TaskRepository taskRepository;
    @Autowired SystemConfigRepository systemConfigRepository;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PlatformTransactionManager transactionManager;

    private ExecutorService executor;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        String url = environmentOrDefault("LAB_PORTAL_MYSQL_IT_URL", DEFAULT_URL);
        assertDisposableDatabase(url);
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username",
                () -> requiredEnvironment("LAB_PORTAL_MYSQL_IT_USERNAME"));
        registry.add("spring.datasource.password",
                () -> requiredEnvironment("LAB_PORTAL_MYSQL_IT_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.hikari.connection-init-sql",
                () -> "SET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.baseline-on-migrate", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.MySQLDialect");
        registry.add("spring.jpa.properties.hibernate.connection.isolation", () -> "4");
    }

    @BeforeAll
    void startExecutor() {
        executor = Executors.newFixedThreadPool(4);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @AfterAll
    void stopExecutor() throws InterruptedException {
        executor.shutdownNow();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    @Test
    void harnessUsesMySql8InnoDbRepeatableReadAndAllRepositoryMigrations() {
        String version = jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
        String isolation = jdbcTemplate.queryForObject(
                "SELECT @@SESSION.transaction_isolation", String.class);
        Integer innodbTables = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN (
                      'users', 'user_roles', 'laboratories', 'projects',
                      'research_groups', 'group_members', 'tasks', 'reports',
                      'audit_logs', 'system_configs'
                  )
                  AND engine = 'InnoDB'
                """, Integer.class);
        Integer migration55 = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM flyway_schema_history
                WHERE version = '55' AND success = 1
                """, Integer.class);

        assertNotNull(version);
        assertTrue(version.startsWith("8."), "Expected MySQL 8 but got " + version);
        assertEquals("REPEATABLE-READ", isolation.toUpperCase(Locale.ROOT));
        assertEquals(10, innodbTables);
        assertEquals(1, migration55);
    }

    @Test
    void currentActorReadObservesCommittedDeactivation() {
        ManagerScenario scenario = managerScenario("actor-inactive", TaskStatus.TODO, false, false);

        assertDeniedAfterCommittedChange(
                scenario.task(),
                scenario.actor().getUsername(),
                () -> jdbcTemplate.update(
                        "UPDATE users SET active = false WHERE id = ?", scenario.actor().getId()),
                TaskStatus.IN_PROGRESS);
    }

    @Test
    void currentActorReadObservesCommittedSoftDelete() {
        ManagerScenario scenario = managerScenario("actor-deleted", TaskStatus.TODO, false, false);

        assertDeniedAfterCommittedChange(
                scenario.task(),
                scenario.actor().getUsername(),
                () -> jdbcTemplate.update(
                        "UPDATE users SET deleted = true WHERE id = ?", scenario.actor().getId()),
                TaskStatus.IN_PROGRESS);
    }

    @Test
    void currentActorReadObservesCommittedManagerRoleRemoval() {
        ManagerScenario scenario = managerScenario("actor-role", TaskStatus.TODO, false, false);
        Long managerRoleId = roleRepository.findByName("LAB_MANAGER").orElseThrow().getId();

        assertDeniedAfterCommittedChange(
                scenario.task(),
                scenario.actor().getUsername(),
                () -> jdbcTemplate.update(
                        "DELETE FROM user_roles WHERE user_id = ? AND role_id = ?",
                        scenario.actor().getId(), managerRoleId),
                TaskStatus.IN_PROGRESS);
    }

    @Test
    void exactLaboratoryReadObservesCommittedManagerLoss() {
        ManagerScenario scenario = managerScenario("manager-loss", TaskStatus.TODO, false, false);
        User replacement = createUser(unique("manager-loss-replacement"), null);

        assertDeniedAfterCommittedChange(
                scenario.task(),
                scenario.actor().getUsername(),
                () -> jdbcTemplate.update(
                        "UPDATE laboratories SET manager_id = ? WHERE id = ?",
                        replacement.getId(), scenario.laboratory().getId()),
                TaskStatus.IN_PROGRESS);
    }

    @Test
    void currentProjectReadObservesCommittedDeactivation() {
        ManagerScenario scenario = managerScenario("project-inactive", TaskStatus.TODO, false, false);

        assertDeniedAfterCommittedChange(
                scenario.task(),
                scenario.actor().getUsername(),
                () -> jdbcTemplate.update(
                        "UPDATE projects SET active = false WHERE id = ?", scenario.project().getId()),
                TaskStatus.IN_PROGRESS);
    }

    @Test
    void currentProjectReadObservesCommittedSoftDelete() {
        ManagerScenario scenario = managerScenario("project-deleted", TaskStatus.TODO, false, false);

        assertDeniedAfterCommittedChange(
                scenario.task(),
                scenario.actor().getUsername(),
                () -> jdbcTemplate.update(
                        "UPDATE projects SET deleted = true WHERE id = ?", scenario.project().getId()),
                TaskStatus.IN_PROGRESS);
    }

    @Test
    void currentGroupReadObservesCommittedDeactivation() {
        ManagerScenario scenario = managerScenario("group-inactive", TaskStatus.TODO, true, false);

        assertDeniedAfterCommittedChange(
                scenario.task(),
                scenario.actor().getUsername(),
                () -> jdbcTemplate.update(
                        "UPDATE research_groups SET active = false WHERE id = ?", scenario.group().getId()),
                TaskStatus.IN_PROGRESS);
    }

    @Test
    void currentGroupReadObservesCommittedSoftDelete() {
        ManagerScenario scenario = managerScenario("group-deleted", TaskStatus.TODO, true, false);

        assertDeniedAfterCommittedChange(
                scenario.task(),
                scenario.actor().getUsername(),
                () -> jdbcTemplate.update(
                        "UPDATE research_groups SET deleted = true WHERE id = ?", scenario.group().getId()),
                TaskStatus.IN_PROGRESS);
    }

    @Test
    void currentProjectReadRejectsCommittedLaboratoryDriftFromGroup() {
        ManagerScenario scenario = managerScenario("project-lab-drift", TaskStatus.TODO, true, false);
        Laboratory otherManagedLab = createLaboratory(
                unique("project-lab-drift-other"), scenario.actor());

        assertDeniedAfterCommittedChange(
                scenario.task(),
                scenario.actor().getUsername(),
                () -> jdbcTemplate.update(
                        "UPDATE projects SET lab_id = ? WHERE id = ?",
                        otherManagedLab.getId(), scenario.project().getId()),
                TaskStatus.IN_PROGRESS);
    }

    @Test
    void currentGroupReadRejectsCommittedLaboratoryDriftFromProject() {
        ManagerScenario scenario = managerScenario("group-lab-drift", TaskStatus.TODO, true, false);
        Laboratory otherManagedLab = createLaboratory(
                unique("group-lab-drift-other"), scenario.actor());

        assertDeniedAfterCommittedChange(
                scenario.task(),
                scenario.actor().getUsername(),
                () -> jdbcTemplate.update(
                        "UPDATE research_groups SET lab_id = ? WHERE id = ?",
                        otherManagedLab.getId(), scenario.group().getId()),
                TaskStatus.IN_PROGRESS);
    }

    @Test
    void currentMembershipReadObservesCommittedRemoval() {
        StudentScenario scenario = studentScenario("membership-removed", 1);

        assertDeniedAfterCommittedChange(
                scenario.tasks().getFirst(),
                scenario.actor().getUsername(),
                () -> jdbcTemplate.update(
                        "UPDATE group_members SET active = false WHERE id = ?",
                        scenario.membership().getId()),
                TaskStatus.IN_PROGRESS);
    }

    @Test
    void doneGateCurrentReadObservesCommittedReportApproval() {
        ManagerScenario scenario = managerScenario("done-report", TaskStatus.IN_REVIEW, false, true);
        ReportEntity report = reportRepository.saveAndFlush(ReportEntity.builder()
                .projectId(scenario.project().getId())
                .milestoneId(scenario.milestone().getId())
                .taskId(scenario.task().getId())
                .submittedById(scenario.actor().getId())
                .version(1)
                .title("MySQL current-read report")
                .contentDone("Complete")
                .result("Result")
                .difficulty("None")
                .nextPlan("Continue")
                .selfAssessment("Good")
                .fileUrl("https://files.example.test/mysql-it.pdf")
                .fileName("mysql-it.pdf")
                .status(ReportStatus.SUBMITTED)
                .submissionScope(unique("done-report-scope"))
                .build());

        TaskResponse response = runAfterCommittedChange(
                scenario.task(),
                scenario.actor().getUsername(),
                () -> jdbcTemplate.update(
                        "UPDATE reports SET status = 'APPROVED' WHERE id = ?", report.getId()),
                () -> taskService.patchResearchTaskStatus(
                        scenario.task().getId(), request(TaskStatus.DONE)));

        assertEquals(TaskStatus.DONE, response.getStatus());
        assertTaskState(scenario.task().getId(), TaskStatus.DONE, 100);
        assertEquals(1, statusAuditCount(scenario.task().getId()));
    }

    @Test
    void doneGateCurrentConfigReadObservesCommittedEnablementAndRejectsWithoutApprovedReport()
            throws Exception {
        ManagerScenario scenario =
                managerScenario("done-config", TaskStatus.IN_REVIEW, false, false);
        SystemConfigEntity configEntity = new SystemConfigEntity();
        configEntity.setConfigKey("GLOBAL_SYSTEM_CONFIG");
        configEntity.setConfigValueJson(
                objectMapper.writeValueAsString(persistedConfig(false)));
        configEntity = systemConfigRepository.saveAndFlush(configEntity);
        Long configId = configEntity.getId();
        String enabledConfigJson =
                objectMapper.writeValueAsString(persistedConfig(true));

        try {
            assertThrows(IllegalArgumentException.class,
                    () -> runAfterCommittedChange(
                            scenario.task(),
                            scenario.actor().getUsername(),
                            () -> jdbcTemplate.update(
                                    """
                                    UPDATE system_configs
                                    SET config_value_json = ?
                                    WHERE id = ?
                                    """,
                                    enabledConfigJson,
                                    configId),
                            () -> taskService.patchResearchTaskStatus(
                                    scenario.task().getId(), request(TaskStatus.DONE))));

            assertTaskState(scenario.task().getId(), TaskStatus.IN_REVIEW, 0);
            assertEquals(0, statusAuditCount(scenario.task().getId()));
        } finally {
            systemConfigRepository.deleteById(configId);
        }
    }

    @Test
    void auditInsertFailureRollsBackTaskAndAuditAtomically() {
        ManagerScenario scenario = managerScenario("audit-rollback", TaskStatus.TODO, false, false);
        String triggerName = "mysql_it_status_audit_" + SEQUENCE.incrementAndGet();
        jdbcTemplate.execute("""
                CREATE TRIGGER `%s`
                BEFORE INSERT ON audit_logs
                FOR EACH ROW
                SIGNAL SQLSTATE '45000'
                    SET MESSAGE_TEXT = 'forced MySQL task-status audit failure'
                """.formatted(triggerName));
        try {
            assertThrows(RuntimeException.class,
                    () -> authenticated(
                            scenario.actor().getUsername(),
                            () -> taskService.patchResearchTaskStatus(
                                    scenario.task().getId(), request(TaskStatus.IN_PROGRESS))));
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS `" + triggerName + "`");
        }

        assertTaskState(scenario.task().getId(), TaskStatus.TODO, 0);
        assertEquals(0, statusAuditCount(scenario.task().getId()));
    }

    @Test
    void fixedTaskActorProjectGroupLaboratoryMembershipOrderHasNoRepresentativeDeadlock()
            throws Exception {
        StudentScenario scenario = studentScenario("lock-order", 2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<TaskResponse> first = executor.submit(concurrentStatusCall(
                scenario.actor().getUsername(), scenario.tasks().get(0).getId(), ready, start));
        Future<TaskResponse> second = executor.submit(concurrentStatusCall(
                scenario.actor().getUsername(), scenario.tasks().get(1).getId(), ready, start));

        assertTrue(ready.await(5, TimeUnit.SECONDS), "Both status calls must be ready");
        start.countDown();

        assertEquals(TaskStatus.IN_PROGRESS,
                first.get(SERVICE_TIMEOUT.toSeconds(), TimeUnit.SECONDS).getStatus());
        assertEquals(TaskStatus.IN_PROGRESS,
                second.get(SERVICE_TIMEOUT.toSeconds(), TimeUnit.SECONDS).getStatus());
        assertTaskState(scenario.tasks().get(0).getId(), TaskStatus.IN_PROGRESS, 10);
        assertTaskState(scenario.tasks().get(1).getId(), TaskStatus.IN_PROGRESS, 10);
    }

    private Callable<TaskResponse> concurrentStatusCall(
            String username,
            Long taskId,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        return () -> authenticated(username, () -> {
            ready.countDown();
            try {
                assertTrue(start.await(5, TimeUnit.SECONDS), "Concurrent calls must be released");
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted before concurrent status call", ex);
            }
            return taskService.patchResearchTaskStatus(taskId, request(TaskStatus.IN_PROGRESS));
        });
    }

    private void assertDeniedAfterCommittedChange(
            TaskEntity task,
            String username,
            Runnable committedChange,
            TaskStatus targetStatus
    ) {
        assertThrows(AccessDeniedException.class,
                () -> runAfterCommittedChange(
                        task,
                        username,
                        committedChange,
                        () -> taskService.patchResearchTaskStatus(
                                task.getId(), request(targetStatus))));
        assertTaskState(task.getId(), task.getStatus(), task.getProgressPercent());
        assertEquals(0, statusAuditCount(task.getId()));
    }

    private <T> T runAfterCommittedChange(
            TaskEntity task,
            String username,
            Runnable committedChange,
            Callable<T> serviceCall
    ) {
        AtomicReference<Future<T>> serviceFuture = new AtomicReference<>();
        TransactionTemplate blocker = transaction(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        blocker.executeWithoutResult(status -> {
            taskRepository.findByIdForUpdate(task.getId()).orElseThrow();
            int baselineWaits = dataLockWaitCount();
            serviceFuture.set(executor.submit(() -> authenticated(username, serviceCall)));
            awaitAdditionalDataLockWait(baselineWaits, serviceFuture.get());
            transaction(TransactionDefinition.PROPAGATION_REQUIRES_NEW)
                    .executeWithoutResult(inner -> committedChange.run());
        });

        try {
            return serviceFuture.get().get(SERVICE_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while awaiting task status update", ex);
        } catch (TimeoutException ex) {
            throw new IllegalStateException(
                    "Task status update did not finish after the blocker released its task lock", ex);
        } catch (ExecutionException ex) {
            throw propagate(ex.getCause());
        }
    }

    private void awaitAdditionalDataLockWait(int baselineWaits, Future<?> serviceFuture) {
        long deadline = System.nanoTime() + LOCK_WAIT_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (serviceFuture.isDone()) {
                try {
                    serviceFuture.get();
                    throw new AssertionError(
                            "Status call completed before reaching the held task row lock");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while checking lock waiter", ex);
                } catch (ExecutionException ex) {
                    throw propagate(ex.getCause());
                }
            }
            if (dataLockWaitCount() > baselineWaits) {
                return;
            }
            try {
                Thread.sleep(25);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while awaiting MySQL data lock", ex);
            }
        }
        throw new AssertionError(
                "MySQL did not expose the service call in performance_schema.data_lock_waits");
    }

    private int dataLockWaitCount() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM performance_schema.data_lock_waits", Integer.class);
        return count == null ? 0 : count;
    }

    private TransactionTemplate transaction(int propagation) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(propagation);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        return template;
    }

    private ManagerScenario managerScenario(
            String label,
            TaskStatus status,
            boolean withGroup,
            boolean withMilestone
    ) {
        User actor = createUser(unique(label + "-manager"), "LAB_MANAGER");
        Laboratory laboratory = createLaboratory(unique(label + "-lab"), actor);
        ProjectEntity project = projectRepository.saveAndFlush(ProjectEntity.builder()
                .lab(laboratory)
                .title(unique(label + "-project"))
                .build());
        GroupEntity group = null;
        if (withGroup) {
            group = groupRepository.saveAndFlush(GroupEntity.builder()
                    .lab(laboratory)
                    .leader(actor)
                    .name(unique(label + "-group"))
                    .build());
        }
        MilestoneEntity milestone = null;
        if (withMilestone) {
            milestone = milestoneRepository.saveAndFlush(MilestoneEntity.builder()
                    .project(project)
                    .name(unique(label + "-milestone"))
                    .title(unique(label + "-milestone-title"))
                    .startDate(LocalDate.now())
                    .endDate(LocalDate.now().plusDays(1))
                    .deadline(LocalDate.now().plusDays(1))
                    .createdBy(actor)
                    .build());
        }
        TaskEntity task = saveTask(
                unique(label + "-task"),
                project.getId(),
                group == null ? null : group.getId(),
                milestone == null ? null : milestone.getId(),
                null,
                status);
        return new ManagerScenario(actor, laboratory, project, group, milestone, task);
    }

    private StudentScenario studentScenario(String label, int taskCount) {
        User actor = createUser(unique(label + "-student"), "STUDENT");
        Laboratory laboratory = createLaboratory(unique(label + "-lab"), actor);
        ProjectEntity project = projectRepository.saveAndFlush(ProjectEntity.builder()
                .lab(laboratory)
                .title(unique(label + "-project"))
                .build());
        GroupEntity group = groupRepository.saveAndFlush(GroupEntity.builder()
                .lab(laboratory)
                .leader(actor)
                .name(unique(label + "-group"))
                .build());
        GroupMemberEntity membership = groupMemberRepository.saveAndFlush(
                GroupMemberEntity.builder()
                        .group(group)
                        .user(actor)
                        .role(GroupRole.LEADER)
                        .build());
        List<TaskEntity> tasks = java.util.stream.IntStream.range(0, taskCount)
                .mapToObj(index -> saveTask(
                        unique(label + "-task-" + index),
                        project.getId(),
                        group.getId(),
                        null,
                        actor.getId(),
                        TaskStatus.TODO))
                .toList();
        return new StudentScenario(actor, project, group, membership, tasks);
    }

    private TaskEntity saveTask(
            String title,
            Long projectId,
            Long groupId,
            Long milestoneId,
            Long assigneeId,
            TaskStatus status
    ) {
        return taskRepository.saveAndFlush(TaskEntity.builder()
                .projectId(projectId)
                .groupId(groupId)
                .milestoneId(milestoneId)
                .assigneeId(assigneeId)
                .title(title)
                .status(status)
                .priority(TaskPriority.MEDIUM)
                .type(TaskType.TASK)
                .progressPercent(0)
                .build());
    }

    private User createUser(String token, String roleName) {
        User user = new User();
        user.setUsername(trimTo(token, 50));
        user.setEmail(trimTo(token, 80) + "@mysql-it.example.test");
        user.setPassword("not-a-real-credential");
        user.setActive(true);
        user.setDeleted(false);
        if (roleName != null) {
            Role role = roleRepository.findByName(roleName).orElseThrow();
            user.addRole(role);
        }
        return userRepository.saveAndFlush(user);
    }

    private Laboratory createLaboratory(String token, User manager) {
        Laboratory laboratory = new Laboratory();
        laboratory.setLabName(trimTo(token, 100));
        laboratory.setLocation("MySQL integration test");
        laboratory.setCapacity(10);
        laboratory.setManager(manager);
        return laboratoryRepository.saveAndFlush(laboratory);
    }

    private PatchTaskStatusRequest request(TaskStatus status) {
        PatchTaskStatusRequest request = new PatchTaskStatusRequest();
        request.setStatus(status);
        return request;
    }

    private SystemConfigResponse persistedConfig(boolean requireApprovedReport) {
        return new SystemConfigResponse(
                new SystemConfigResponse.AccountConfig(true, "STUDENT", 5),
                new SystemConfigResponse.LabConfig(true, true, true, true),
                new SystemConfigResponse.BookingConfig(10, 30, true, true),
                new SystemConfigResponse.UploadConfig(
                        10, 50,
                        List.of("pdf", "doc", "docx"),
                        List.of("pdf", "doc", "docx", "zip")),
                new SystemConfigResponse.ResearchConfig(
                        10, requireApprovedReport, true, true));
    }

    private void assertTaskState(Long taskId, TaskStatus status, int progress) {
        TaskEntity task = taskRepository.findById(taskId).orElseThrow();
        assertEquals(status, task.getStatus());
        assertEquals(progress, task.getProgressPercent());
    }

    private int statusAuditCount(Long taskId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM audit_logs
                WHERE action = 'UPDATE_RESEARCH_TASK_STATUS'
                  AND target_id = ?
                """, Integer.class, taskId);
        return count == null ? 0 : count;
    }

    private <T> T authenticated(String username, Callable<T> action) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, List.of()));
        try {
            return action.call();
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Authenticated test action failed", ex);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static RuntimeException propagate(Throwable cause) {
        if (cause instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        return new IllegalStateException("Task status worker failed", cause);
    }

    private static String unique(String label) {
        return "mysql-it-" + trimTo(label, 24) + "-" + SEQUENCE.incrementAndGet();
    }

    private static String trimTo(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set for the opt-in MySQL integration suite");
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

    private record ManagerScenario(
            User actor,
            Laboratory laboratory,
            ProjectEntity project,
            GroupEntity group,
            MilestoneEntity milestone,
            TaskEntity task
    ) {
    }

    private record StudentScenario(
            User actor,
            ProjectEntity project,
            GroupEntity group,
            GroupMemberEntity membership,
            List<TaskEntity> tasks
    ) {
    }
}
