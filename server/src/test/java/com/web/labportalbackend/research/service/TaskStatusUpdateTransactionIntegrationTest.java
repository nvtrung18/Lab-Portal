package com.web.labportalbackend.research.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.labportalbackend.admin.audit.enums.AuditAction;
import com.web.labportalbackend.admin.audit.repository.AuditLogRepository;
import com.web.labportalbackend.admin.audit.service.AuditLogService;
import com.web.labportalbackend.admin.systemconfig.dto.SystemConfigResponse;
import com.web.labportalbackend.admin.systemconfig.entity.SystemConfigEntity;
import com.web.labportalbackend.admin.systemconfig.repository.SystemConfigRepository;
import com.web.labportalbackend.admin.systemconfig.service.SystemConfigService;
import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.RoleRepository;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.dto.request.PatchTaskStatusRequest;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.GroupMemberEntity;
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
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.repository.ReportRepository;
import com.web.labportalbackend.research.repository.TaskRepository;
import com.web.labportalbackend.research.security.TaskPermissionHelper;
import com.web.labportalbackend.research.service.impl.TaskStatusUpdateService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.aop.support.AopUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@TestPropertySource(properties = "spring.jpa.properties.hibernate.connection.isolation=4")
class TaskStatusUpdateTransactionIntegrationTest {

    @Autowired TaskService taskService;
    @Autowired UserRepository userRepository;
    @Autowired RoleRepository roleRepository;
    @Autowired LaboratoryRepository laboratoryRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired GroupRepository groupRepository;
    @Autowired GroupMemberRepository groupMemberRepository;
    @Autowired AuditLogRepository auditLogRepository;
    @Autowired SystemConfigRepository systemConfigRepository;
    @Autowired ObjectMapper objectMapper;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoSpyBean TaskRepository taskRepository;
    @MockitoSpyBean ReportRepository reportRepository;
    @MockitoSpyBean AuditLogService auditLogService;
    @MockitoSpyBean TaskPermissionHelper taskPermissionHelper;
    @MockitoSpyBean SystemConfigService systemConfigService;

    @AfterEach
    void clearSecurityAndSpies() {
        SecurityContextHolder.clearContext();
        reset(taskRepository, reportRepository, auditLogService, taskPermissionHelper, systemConfigService);
    }

    @Test
    void statusAndAuditCommitTogetherThroughSpringProxy() {
        TaskEntity task = fixture("commit", TaskStatus.TODO, null);
        long auditsBefore = statusAudits(task.getId());

        assertTrue(AopUtils.isAopProxy(taskService));
        taskService.patchResearchTaskStatus(task.getId(), request(TaskStatus.IN_PROGRESS, null));

        TaskEntity reloaded = taskRepository.findById(task.getId()).orElseThrow();
        assertEquals(TaskStatus.IN_PROGRESS, reloaded.getStatus());
        assertEquals(10, reloaded.getProgressPercent());
        assertEquals(auditsBefore + 1, statusAudits(task.getId()));
        String metadata = statusAuditMetadata(task.getId());
        assertTrue(metadata.contains("\"fromStatus\":\"TODO\""), metadata);
        assertTrue(metadata.contains("\"toStatus\":\"IN_PROGRESS\""), metadata);
        assertTrue(metadata.contains("\"actorCapability\":\"MANAGER\""), metadata);
        assertTrue(metadata.contains("\"blockedReasonChanged\":false"), metadata);
        assertTrue(metadata.contains("\"progressChanged\":true"), metadata);
    }

    @Test
    void auditFailureRollsBackStatusProgressAndAudit() {
        TaskEntity task = fixture("audit-rollback", TaskStatus.BLOCKED, null);
        task.setBlockedReason("persisted blocker");
        taskRepository.saveAndFlush(task);
        long auditsBefore = statusAudits(task.getId());
        doThrow(new RuntimeException("audit unavailable"))
                .when(auditLogService)
                .log(any(), any(), any(), anyString(), anyLong(), anyString(), anyString());

        assertThrows(RuntimeException.class,
                () -> taskService.patchResearchTaskStatus(
                        task.getId(), request(TaskStatus.IN_PROGRESS, null)));

        TaskEntity reloaded = taskRepository.findById(task.getId()).orElseThrow();
        assertEquals(TaskStatus.BLOCKED, reloaded.getStatus());
        assertEquals("persisted blocker", reloaded.getBlockedReason());
        assertEquals(0, reloaded.getProgressPercent());
        assertEquals(auditsBefore, statusAudits(task.getId()));
        verify(taskRepository).save(any(TaskEntity.class));
    }

    @Test
    void taskSaveFailureRollsBackAndSkipsAudit() {
        TaskEntity task = fixture("save-rollback", TaskStatus.TODO, null);
        long auditsBefore = statusAudits(task.getId());
        doThrow(new IllegalStateException("save failed"))
                .when(taskRepository).save(any(TaskEntity.class));

        assertThrows(RuntimeException.class,
                () -> taskService.patchResearchTaskStatus(
                        task.getId(), request(TaskStatus.IN_PROGRESS, null)));

        assertEquals(TaskStatus.TODO, taskRepository.findById(task.getId()).orElseThrow().getStatus());
        assertEquals(auditsBefore, statusAudits(task.getId()));
        verify(auditLogService, never()).log(any(), any(), any(), anyString(), anyLong(), anyString(), anyString());
    }

    @Test
    void pureNoOpKeepsPersistedTimestampAndSkipsSaveAndAudit() {
        TaskEntity task = fixture("no-op", TaskStatus.TODO, null);
        var updatedAtBefore = taskRepository.findById(task.getId()).orElseThrow().getUpdatedAt();
        long auditsBefore = statusAudits(task.getId());
        clearInvocations(taskRepository, auditLogService, taskPermissionHelper);

        taskService.patchResearchTaskStatus(task.getId(), request(TaskStatus.TODO, null));

        TaskEntity reloaded = taskRepository.findById(task.getId()).orElseThrow();
        assertEquals(updatedAtBefore, reloaded.getUpdatedAt());
        assertEquals(auditsBefore, statusAudits(task.getId()));
        verify(taskRepository, never()).save(any(TaskEntity.class));
        verify(auditLogService, never()).log(any(), any(), any(), anyString(), anyLong(), anyString(), anyString());
    }

    @Test
    void blockedReasonRefreshPersistsAndAuditsWithoutStatusChange() {
        TaskEntity task = fixture("blocked-refresh", TaskStatus.BLOCKED, null);
        task.setBlockedReason("old reason");
        taskRepository.saveAndFlush(task);
        long auditsBefore = statusAudits(task.getId());

        taskService.patchResearchTaskStatus(
                task.getId(), request(TaskStatus.BLOCKED, "  new reason  "));

        TaskEntity reloaded = taskRepository.findById(task.getId()).orElseThrow();
        assertEquals(TaskStatus.BLOCKED, reloaded.getStatus());
        assertEquals("new reason", reloaded.getBlockedReason());
        assertEquals(auditsBefore + 1, statusAudits(task.getId()));
    }

    @Test
    void nullMilestoneManagerTransitionDoesNotCallMilestoneFlow() {
        TaskEntity task = fixture("null-milestone", TaskStatus.BACKLOG, null);

        var response = taskService.patchResearchTaskStatus(
                task.getId(), request(TaskStatus.TODO, null));

        assertEquals(TaskStatus.TODO, response.getStatus());
        assertNull(response.getMilestoneId());
        assertEquals(0, taskRepository.findById(task.getId()).orElseThrow().getProgressPercent());
    }

    @Test
    void nullMilestoneLeaderTransitionUsesCurrentMembership() {
        StudentFixture fixture = studentFixture("null-milestone-leader", GroupRole.LEADER, false);

        var response = taskService.patchResearchTaskStatus(
                fixture.task().getId(), request(TaskStatus.IN_PROGRESS, null));

        assertEquals(TaskStatus.IN_PROGRESS, response.getStatus());
        assertNull(response.getMilestoneId());
        assertEquals(10, taskRepository.findById(fixture.task().getId()).orElseThrow().getProgressPercent());
    }

    @Test
    void nullMilestoneAssignedMemberCanSubmitForReview() {
        StudentFixture fixture = studentFixture("null-milestone-member", GroupRole.MEMBER, true);
        fixture.task().setStatus(TaskStatus.IN_PROGRESS);
        fixture.task().setProgressPercent(30);
        taskRepository.saveAndFlush(fixture.task());

        var response = taskService.patchResearchTaskStatus(
                fixture.task().getId(), request(TaskStatus.IN_REVIEW, null));

        assertEquals(TaskStatus.IN_REVIEW, response.getStatus());
        assertNull(response.getMilestoneId());
        assertEquals(90, taskRepository.findById(fixture.task().getId()).orElseThrow().getProgressPercent());
    }

    @Test
    void nullMilestoneAssignedMemberCanStartWork() {
        StudentFixture fixture = studentFixture("null-milestone-assignee-start", GroupRole.MEMBER, true);

        var response = taskService.patchResearchTaskStatus(
                fixture.task().getId(), request(TaskStatus.IN_PROGRESS, null));

        assertEquals(TaskStatus.IN_PROGRESS, response.getStatus());
        assertNull(response.getMilestoneId());
        assertEquals(10, taskRepository.findById(fixture.task().getId()).orElseThrow().getProgressPercent());
    }

    @Test
    void freshLockedActorStateFailsClosedAfterCommittedDeactivation() {
        TaskEntity task = fixture("fresh-actor", TaskStatus.TODO, null);
        Long actorId = userRepository.findByUsername(currentUsername()).orElseThrow().getId();
        AtomicBoolean flipped = new AtomicBoolean();
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        doAnswer(invocation -> {
            if (flipped.compareAndSet(false, true)) {
                requiresNew.executeWithoutResult(status -> {
                    User actor = userRepository.findById(actorId).orElseThrow();
                    actor.setActive(false);
                    userRepository.saveAndFlush(actor);
                });
            }
            return true;
        }).when(taskPermissionHelper)
                .isManagerOfTaskProjectOrLab(eq(actorId), any(TaskEntity.class));

        assertThrows(AccessDeniedException.class,
                () -> taskService.patchResearchTaskStatus(
                        task.getId(), request(TaskStatus.IN_PROGRESS, null)));

        assertEquals(TaskStatus.TODO, taskRepository.findById(task.getId()).orElseThrow().getStatus());
        assertEquals(0, statusAudits(task.getId()));
    }

    @Test
    void freshLockedActorStateFailsClosedAfterCommittedSoftDelete() {
        TaskEntity task = fixture("fresh-actor-deleted", TaskStatus.TODO, null);
        Long actorId = userRepository.findByUsername(currentUsername()).orElseThrow().getId();
        installManagerPhaseChange(actorId, () -> {
            User actor = userRepository.findById(actorId).orElseThrow();
            actor.setDeleted(true);
            userRepository.saveAndFlush(actor);
        });

        assertThrows(AccessDeniedException.class,
                () -> taskService.patchResearchTaskStatus(
                        task.getId(), request(TaskStatus.IN_PROGRESS, null)));

        assertEquals(TaskStatus.TODO, taskRepository.findById(task.getId()).orElseThrow().getStatus());
        assertEquals(0, statusAudits(task.getId()));
    }

    @Test
    void freshLockedActorRolesObserveCommittedManagerRoleRemoval() {
        TaskEntity task = fixture("fresh-role", TaskStatus.TODO, null);
        User actor = userRepository.findByUsername(currentUsername()).orElseThrow();
        Role managerRole = roleRepository.findByName("LAB_MANAGER").orElseThrow();
        installManagerPhaseChange(actor.getId(), () -> {
            User current = userRepository.findById(actor.getId()).orElseThrow();
            current.removeRole(managerRole);
            userRepository.saveAndFlush(current);
        });

        assertThrows(AccessDeniedException.class,
                () -> taskService.patchResearchTaskStatus(
                        task.getId(), request(TaskStatus.IN_PROGRESS, null)));

        assertEquals(TaskStatus.TODO, taskRepository.findById(task.getId()).orElseThrow().getStatus());
        assertEquals(0, statusAudits(task.getId()));
    }

    @Test
    void committedManagerRoleRemovalFallsBackToFreshLeaderCapability() {
        StudentFixture fixture = studentFixture("fresh-role-leader-fallback", GroupRole.LEADER, false);
        User actor = addRole(fixture.actorId(), "LAB_MANAGER");
        Role managerRole = roleRepository.findByName("LAB_MANAGER").orElseThrow();
        installManagerPhaseChange(actor.getId(), () -> {
            User current = userRepository.findById(actor.getId()).orElseThrow();
            current.removeRole(managerRole);
            userRepository.saveAndFlush(current);
        });

        taskService.patchResearchTaskStatus(
                fixture.task().getId(), request(TaskStatus.IN_PROGRESS, null));

        assertEquals(TaskStatus.IN_PROGRESS,
                taskRepository.findById(fixture.task().getId()).orElseThrow().getStatus());
        String metadata = statusAuditMetadata(fixture.task().getId());
        assertTrue(metadata.contains("actorCapability") && metadata.contains("LEADER"), metadata);
    }

    @Test
    void dualRoleActorUsesLeaderWhenExactManagerScopeIsAbsent() {
        StudentFixture fixture = studentFixture("dual-leader", GroupRole.LEADER, false);
        addRole(fixture.actorId(), "LAB_MANAGER");
        moveTaskLaboratoryToAnotherManager(fixture.task(), "dual-leader-replacement");

        taskService.patchResearchTaskStatus(
                fixture.task().getId(), request(TaskStatus.IN_PROGRESS, null));

        assertEquals(TaskStatus.IN_PROGRESS,
                taskRepository.findById(fixture.task().getId()).orElseThrow().getStatus());
        String metadata = statusAuditMetadata(fixture.task().getId());
        assertTrue(metadata.contains("actorCapability") && metadata.contains("LEADER"), metadata);
    }

    @Test
    void dualRoleActorUsesMemberWhenExactManagerScopeIsAbsent() {
        StudentFixture fixture = studentFixture("dual-member", GroupRole.MEMBER, true);
        addRole(fixture.actorId(), "LAB_MANAGER");
        moveTaskLaboratoryToAnotherManager(fixture.task(), "dual-member-replacement");

        taskService.patchResearchTaskStatus(
                fixture.task().getId(), request(TaskStatus.IN_PROGRESS, null));

        assertEquals(TaskStatus.IN_PROGRESS,
                taskRepository.findById(fixture.task().getId()).orElseThrow().getStatus());
        String metadata = statusAuditMetadata(fixture.task().getId());
        assertTrue(metadata.contains("actorCapability") && metadata.contains("MEMBER"), metadata);
    }

    @Test
    void dualRoleActorUsesManagerPrecedenceWhenAllCapabilitiesApply() {
        StudentFixture fixture = studentFixture("dual-manager", GroupRole.LEADER, true);
        addRole(fixture.actorId(), "LAB_MANAGER");

        taskService.patchResearchTaskStatus(
                fixture.task().getId(), request(TaskStatus.IN_PROGRESS, null));

        assertEquals(TaskStatus.IN_PROGRESS,
                taskRepository.findById(fixture.task().getId()).orElseThrow().getStatus());
        String metadata = statusAuditMetadata(fixture.task().getId());
        assertTrue(metadata.contains("actorCapability") && metadata.contains("MANAGER"), metadata);
    }

    @Test
    void dualRoleActorWithNoExactManagerOrStudentCapabilityIsDeniedBeforeTaskLock() {
        StudentFixture fixture = studentFixture("dual-no-capability", GroupRole.MEMBER, false);
        addRole(fixture.actorId(), "LAB_MANAGER");
        moveTaskLaboratoryToAnotherManager(fixture.task(), "dual-no-capability-replacement");
        clearInvocations(taskRepository, auditLogService, taskPermissionHelper);

        assertThrows(AccessDeniedException.class,
                () -> taskService.patchResearchTaskStatus(
                        fixture.task().getId(), request(TaskStatus.IN_PROGRESS, null)));

        verify(taskRepository, never()).findByIdForUpdate(anyLong());
        verify(auditLogService, never()).log(any(), any(), any(), anyString(), anyLong(), anyString(), anyString());
    }

    @Test
    void committedManagerOwnershipLossFallsBackToFreshLeaderCapability() {
        StudentFixture fixture = studentFixture("fresh-lab-leader-fallback", GroupRole.LEADER, false);
        addRole(fixture.actorId(), "LAB_MANAGER");
        installManagerPhaseChange(fixture.actorId(),
                () -> moveTaskLaboratoryToAnotherManager(
                        fixture.task(), "fresh-lab-leader-fallback-replacement"));

        taskService.patchResearchTaskStatus(
                fixture.task().getId(), request(TaskStatus.IN_PROGRESS, null));

        assertEquals(TaskStatus.IN_PROGRESS,
                taskRepository.findById(fixture.task().getId()).orElseThrow().getStatus());
        String metadata = statusAuditMetadata(fixture.task().getId());
        assertTrue(metadata.contains("actorCapability") && metadata.contains("LEADER"), metadata);
    }

    @Test
    void currentLabReadObservesCommittedOwnershipLoss() {
        TaskEntity task = fixture("fresh-lab", TaskStatus.TODO, null);
        User actor = userRepository.findByUsername(currentUsername()).orElseThrow();
        ProjectEntity project = projectRepository.findById(task.getProjectId()).orElseThrow();
        Long labId = project.getLab().getId();
        User replacement = user("replacement-lab-owner");
        installManagerPhaseChange(actor.getId(), () -> {
            Laboratory lab = laboratoryRepository.findById(labId).orElseThrow();
            lab.setManager(replacement);
            laboratoryRepository.saveAndFlush(lab);
        });

        assertThrows(AccessDeniedException.class,
                () -> taskService.patchResearchTaskStatus(
                        task.getId(), request(TaskStatus.IN_PROGRESS, null)));

        assertEquals(TaskStatus.TODO, taskRepository.findById(task.getId()).orElseThrow().getStatus());
    }

    @Test
    void currentProjectReadObservesCommittedDeactivation() {
        TaskEntity task = fixture("fresh-project", TaskStatus.TODO, null);
        User actor = userRepository.findByUsername(currentUsername()).orElseThrow();
        installManagerPhaseChange(actor.getId(), () -> {
            ProjectEntity project = projectRepository.findById(task.getProjectId()).orElseThrow();
            project.setActive(false);
            projectRepository.saveAndFlush(project);
        });

        assertThrows(AccessDeniedException.class,
                () -> taskService.patchResearchTaskStatus(
                        task.getId(), request(TaskStatus.IN_PROGRESS, null)));

        assertEquals(TaskStatus.TODO, taskRepository.findById(task.getId()).orElseThrow().getStatus());
    }

    @Test
    void currentGroupReadObservesCommittedDeactivation() {
        StudentFixture fixture = studentFixture("fresh-group", GroupRole.LEADER, false);
        installLeaderPhaseChange(fixture, () -> {
            GroupEntity group = groupRepository.findById(fixture.groupId()).orElseThrow();
            group.setActive(false);
            groupRepository.saveAndFlush(group);
        });

        assertThrows(AccessDeniedException.class,
                () -> taskService.patchResearchTaskStatus(
                        fixture.task().getId(), request(TaskStatus.IN_PROGRESS, null)));

        assertEquals(TaskStatus.TODO,
                taskRepository.findById(fixture.task().getId()).orElseThrow().getStatus());
    }

    @Test
    void currentMembershipReadObservesCommittedRemoval() {
        StudentFixture fixture = studentFixture("fresh-membership", GroupRole.LEADER, false);
        installLeaderPhaseChange(fixture, () -> {
            GroupMemberEntity member = groupMemberRepository.findById(fixture.membershipId()).orElseThrow();
            member.setActive(false);
            groupMemberRepository.saveAndFlush(member);
        });

        assertThrows(AccessDeniedException.class,
                () -> taskService.patchResearchTaskStatus(
                        fixture.task().getId(), request(TaskStatus.IN_PROGRESS, null)));

        assertEquals(TaskStatus.TODO,
                taskRepository.findById(fixture.task().getId()).orElseThrow().getStatus());
    }

    @Test
    void enabledDoneGateRejectsWithoutLatestApprovedReport() {
        TaskEntity task = fixture("done-gate", TaskStatus.IN_REVIEW, null);

        assertThrows(IllegalArgumentException.class,
                () -> taskService.patchResearchTaskStatus(
                        task.getId(), request(TaskStatus.DONE, null)));

        assertEquals(TaskStatus.IN_REVIEW, taskRepository.findById(task.getId()).orElseThrow().getStatus());
    }

    @Test
    void enabledDoneGateUsesCurrentLockingReadAndCommitsWithLatestApprovedReport() {
        TaskEntity task = fixture("done-approved", TaskStatus.IN_REVIEW, null);
        User actor = userRepository.findByUsername(currentUsername()).orElseThrow();
        ReportEntity report = approvedReport(task, actor.getId(), "done-approved");
        reportRepository.saveAndFlush(report);
        clearInvocations(reportRepository);

        taskService.patchResearchTaskStatus(task.getId(), request(TaskStatus.DONE, null));

        TaskEntity reloaded = taskRepository.findById(task.getId()).orElseThrow();
        assertEquals(TaskStatus.DONE, reloaded.getStatus());
        assertEquals(100, reloaded.getProgressPercent());
        verify(reportRepository).findLatestApprovedForStatusAuthorization(task.getId());
        verify(reportRepository, never()).existsLatestApprovedByTaskId(anyLong());
    }

    @Test
    void doneGateUsesCommittedEnabledConfigAfterRepeatableReadSnapshot() throws Exception {
        TaskEntity task = fixture("done-config-current", TaskStatus.IN_REVIEW, null);
        User actor = userRepository.findByUsername(currentUsername()).orElseThrow();
        long auditsBefore = statusAudits(task.getId());
        SystemConfigEntity configEntity = new SystemConfigEntity();
        configEntity.setConfigKey("GLOBAL_SYSTEM_CONFIG");
        configEntity.setConfigValueJson(objectMapper.writeValueAsString(persistedConfig(false)));
        configEntity = systemConfigRepository.saveAndFlush(configEntity);
        Long configId = configEntity.getId();
        String enabledConfigJson = objectMapper.writeValueAsString(persistedConfig(true));

        try {
            installManagerPhaseChange(actor.getId(), () -> {
                SystemConfigEntity current = systemConfigRepository.findById(configId).orElseThrow();
                current.setConfigValueJson(enabledConfigJson);
                systemConfigRepository.saveAndFlush(current);
            });

            assertThrows(IllegalArgumentException.class,
                    () -> taskService.patchResearchTaskStatus(
                            task.getId(), request(TaskStatus.DONE, null)));

            TaskEntity reloaded = taskRepository.findById(task.getId()).orElseThrow();
            assertEquals(TaskStatus.IN_REVIEW, reloaded.getStatus());
            assertEquals(0, reloaded.getProgressPercent());
            assertEquals(auditsBefore, statusAudits(task.getId()));
        } finally {
            systemConfigRepository.deleteById(configId);
        }
    }

    @Test
    void disabledDoneGateCommitsWithoutAnyReportRead() {
        TaskEntity task = fixture("done-disabled", TaskStatus.IN_REVIEW, null);
        doReturn(config(false)).when(systemConfigService).getConfigForStatusAuthorization();
        clearInvocations(reportRepository);

        taskService.patchResearchTaskStatus(task.getId(), request(TaskStatus.DONE, null));

        TaskEntity reloaded = taskRepository.findById(task.getId()).orElseThrow();
        assertEquals(TaskStatus.DONE, reloaded.getStatus());
        assertEquals(100, reloaded.getProgressPercent());
        verify(reportRepository, never()).findLatestApprovedForStatusAuthorization(anyLong());
        verify(reportRepository, never()).existsLatestApprovedByTaskId(anyLong());
    }

    @Test
    void unexpectedNullDoneConfigFailsClosedWithoutTaskOrAuditCommit() {
        TaskEntity task = fixture("done-null-config", TaskStatus.IN_REVIEW, null);
        long auditsBefore = statusAudits(task.getId());
        doReturn(null).when(systemConfigService).getConfigForStatusAuthorization();

        assertThrows(IllegalStateException.class,
                () -> taskService.patchResearchTaskStatus(
                        task.getId(), request(TaskStatus.DONE, null)));

        TaskEntity reloaded = taskRepository.findById(task.getId()).orElseThrow();
        assertEquals(TaskStatus.IN_REVIEW, reloaded.getStatus());
        assertEquals(0, reloaded.getProgressPercent());
        assertEquals(auditsBefore, statusAudits(task.getId()));
        verify(reportRepository, never()).findLatestApprovedForStatusAuthorization(anyLong());
        verify(auditLogService, never()).log(any(), any(), any(), anyString(), anyLong(), anyString(), anyString());
    }

    private TaskEntity fixture(String suffix, TaskStatus status, Long milestoneId) {
        Role managerRole = roleRepository.findByName("LAB_MANAGER")
                .orElseGet(() -> roleRepository.save(new Role("LAB_MANAGER", "Lab manager")));
        User manager = new User();
        manager.setUsername("status-manager-" + suffix);
        manager.setEmail("status-manager-" + suffix + "@example.test");
        manager.setPassword("password");
        manager.setActive(true);
        manager.setDeleted(false);
        manager.addRole(managerRole);
        manager = userRepository.save(manager);

        Laboratory lab = new Laboratory();
        lab.setLabName("Status Lab " + suffix);
        lab.setLocation("Room");
        lab.setCapacity(10);
        lab.setManager(manager);
        lab = laboratoryRepository.save(lab);

        ProjectEntity project = ProjectEntity.builder()
                .lab(lab)
                .title("Status Project " + suffix)
                .build();
        project = projectRepository.save(project);

        TaskEntity task = TaskEntity.builder()
                .projectId(project.getId())
                .milestoneId(milestoneId)
                .title("Status Task " + suffix)
                .status(status)
                .priority(TaskPriority.MEDIUM)
                .type(TaskType.TASK)
                .progressPercent(0)
                .build();
        task = taskRepository.saveAndFlush(task);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(manager.getUsername(), null, List.of()));
        return task;
    }

    private StudentFixture studentFixture(String suffix, GroupRole groupRole, boolean assigned) {
        Role studentRole = roleRepository.findByName("STUDENT")
                .orElseGet(() -> roleRepository.save(new Role("STUDENT", "Student")));
        User student = user("status-student-" + suffix);
        student.addRole(studentRole);
        student = userRepository.save(student);

        Laboratory lab = new Laboratory();
        lab.setLabName("Student Status Lab " + suffix);
        lab.setLocation("Room");
        lab.setCapacity(10);
        lab.setManager(student);
        lab = laboratoryRepository.save(lab);

        ProjectEntity project = ProjectEntity.builder()
                .lab(lab)
                .title("Student Status Project " + suffix)
                .build();
        project = projectRepository.save(project);

        GroupEntity group = GroupEntity.builder()
                .lab(lab)
                .leader(student)
                .name("Student Status Group " + suffix)
                .build();
        group = groupRepository.save(group);

        GroupMemberEntity membership = GroupMemberEntity.builder()
                .group(group)
                .user(student)
                .role(groupRole)
                .build();
        membership = groupMemberRepository.save(membership);

        TaskEntity task = TaskEntity.builder()
                .projectId(project.getId())
                .groupId(group.getId())
                .assigneeId(assigned ? student.getId() : null)
                .title("Student Status Task " + suffix)
                .status(TaskStatus.TODO)
                .priority(TaskPriority.MEDIUM)
                .type(TaskType.TASK)
                .progressPercent(0)
                .build();
        task = taskRepository.saveAndFlush(task);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(student.getUsername(), null, List.of()));
        return new StudentFixture(task, student.getId(), group.getId(), membership.getId());
    }

    private User user(String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.test");
        user.setPassword("password");
        user.setActive(true);
        user.setDeleted(false);
        return userRepository.save(user);
    }

    private User addRole(Long userId, String roleName) {
        Role role = roleRepository.findByName(roleName)
                .orElseGet(() -> roleRepository.save(new Role(roleName, roleName)));
        User actor = userRepository.findById(userId).orElseThrow();
        actor.addRole(role);
        return userRepository.saveAndFlush(actor);
    }

    private void moveTaskLaboratoryToAnotherManager(TaskEntity task, String replacementUsername) {
        User replacement = user(replacementUsername);
        Long laboratoryId = projectRepository.findById(task.getProjectId()).orElseThrow().getLab().getId();
        Laboratory laboratory = laboratoryRepository.findById(laboratoryId).orElseThrow();
        laboratory.setManager(replacement);
        laboratoryRepository.saveAndFlush(laboratory);
    }

    private void installManagerPhaseChange(Long actorId, Runnable committedChange) {
        AtomicBoolean changed = new AtomicBoolean();
        TransactionTemplate requiresNew = requiresNewTransaction();
        doAnswer(invocation -> {
            if (changed.compareAndSet(false, true)) {
                requiresNew.executeWithoutResult(status -> committedChange.run());
            }
            return true;
        }).when(taskPermissionHelper)
                .isManagerOfTaskProjectOrLab(eq(actorId), any(TaskEntity.class));
    }

    private void installLeaderPhaseChange(StudentFixture fixture, Runnable committedChange) {
        AtomicBoolean changed = new AtomicBoolean();
        TransactionTemplate requiresNew = requiresNewTransaction();
        doAnswer(invocation -> {
            if (changed.compareAndSet(false, true)) {
                requiresNew.executeWithoutResult(status -> committedChange.run());
            }
            return true;
        }).when(taskPermissionHelper)
                .isLeaderInTaskGroup(eq(fixture.actorId()), any(TaskEntity.class));
        doReturn(true).when(taskPermissionHelper)
                .isMemberInTaskGroup(eq(fixture.actorId()), any(TaskEntity.class));
    }

    private TransactionTemplate requiresNewTransaction() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private ReportEntity approvedReport(TaskEntity task, Long submittedById, String suffix) {
        return ReportEntity.builder()
                .projectId(task.getProjectId())
                .groupId(task.getGroupId())
                .milestoneId(1L)
                .taskId(task.getId())
                .submittedById(submittedById)
                .version(1)
                .title("Approved report " + suffix)
                .contentDone("Completed")
                .result("Result")
                .difficulty("None")
                .nextPlan("Continue")
                .selfAssessment("Good")
                .fileUrl("https://files.example.test/" + suffix)
                .fileName(suffix + ".pdf")
                .status(ReportStatus.APPROVED)
                .submissionScope("task-status-" + suffix)
                .build();
    }

    private SystemConfigResponse config(boolean requireApprovedReport) {
        return new SystemConfigResponse(
                null, null, null, null,
                new SystemConfigResponse.ResearchConfig(10, requireApprovedReport, true, true));
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

    private PatchTaskStatusRequest request(TaskStatus status, String reason) {
        PatchTaskStatusRequest request = new PatchTaskStatusRequest();
        request.setStatus(status);
        request.setBlockedReason(reason);
        return request;
    }

    private String currentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private long statusAudits(Long taskId) {
        return auditLogRepository.findAll().stream()
                .filter(log -> log.getAction() == AuditAction.UPDATE_RESEARCH_TASK_STATUS)
                .filter(log -> taskId.equals(log.getTargetId()))
                .count();
    }

    private String statusAuditMetadata(Long taskId) {
        return auditLogRepository.findAll().stream()
                .filter(log -> log.getAction() == AuditAction.UPDATE_RESEARCH_TASK_STATUS)
                .filter(log -> taskId.equals(log.getTargetId()))
                .map(log -> log.getMetadataJson())
                .findFirst()
                .orElseThrow();
    }

    private record StudentFixture(TaskEntity task, Long actorId, Long groupId, Long membershipId) {
    }
}
