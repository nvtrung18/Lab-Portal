package com.web.labportalbackend.research.service;

import com.web.labportalbackend.admin.audit.service.AuditLogService;
import com.web.labportalbackend.admin.systemconfig.service.SystemConfigService;
import com.web.labportalbackend.admin.systemconfig.dto.SystemConfigResponse;
import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.research.dto.request.PatchTaskStatusRequest;
import com.web.labportalbackend.research.entity.TaskEntity;
import com.web.labportalbackend.research.enums.TaskStatus;
import com.web.labportalbackend.research.repository.ReportRepository;
import com.web.labportalbackend.research.repository.TaskRepository;
import com.web.labportalbackend.research.security.TaskPermissionHelper;
import com.web.labportalbackend.research.service.impl.TaskStatusUpdateService;
import com.web.labportalbackend.research.service.impl.TaskActivityRecorder;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.InOrder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskStatusUpdateServiceTest {

    @Mock TaskRepository taskRepository;
    @Mock UserRepository userRepository;
    @Mock TaskPermissionHelper taskPermissionHelper;
    @Mock ReportRepository reportRepository;
    @Mock SystemConfigService systemConfigService;
    @Mock TaskWorkflowService taskWorkflowService;
    @Mock AuditLogService auditLogService;
    @Mock EntityManager entityManager;
    @Mock TaskActivityRecorder taskActivityRecorder;

    private TaskStatusUpdateService service;
    private User manager;
    private TaskEntity initial;
    private TaskEntity locked;

    @BeforeEach
    void setUp() {
        service = new TaskStatusUpdateService(taskRepository, userRepository, taskPermissionHelper,
                reportRepository, systemConfigService, taskWorkflowService, auditLogService, entityManager,
                taskActivityRecorder);
        manager = user(2L, "manager", "LAB_MANAGER");
        initial = task(TaskStatus.TODO, 0);
        locked = task(TaskStatus.TODO, 0);
        lenient().when(userRepository.findByUsername("manager")).thenReturn(Optional.of(manager));
        lenient().when(userRepository.findByIdForStatusAuthorization(2L)).thenReturn(Optional.of(manager));
        lenient().when(taskRepository.findByIdAndDeletedFalseAndActiveTrue(20L)).thenReturn(Optional.of(initial));
        lenient().when(taskRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(locked));
        lenient().when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(taskPermissionHelper.isManagerOfTaskProjectOrLab(eq(2L), any(TaskEntity.class))).thenReturn(true);
        lenient().when(taskPermissionHelper.isLeaderInTaskGroup(anyLong(), any(TaskEntity.class))).thenReturn(false);
        lenient().when(taskPermissionHelper.isTaskAssignee(anyLong(), any(TaskEntity.class))).thenReturn(false);
        lenient().when(taskPermissionHelper.isMemberInTaskGroup(anyLong(), any(TaskEntity.class))).thenReturn(false);
        lenient().when(taskPermissionHelper.resolveStatusAuthorizationScope(any(TaskEntity.class)))
                .thenReturn(new TaskPermissionHelper.StatusAuthorizationScope(true, 1L));
        lenient().when(taskPermissionHelper.isLaboratoryManagedByForStatusAuthorization(1L, 2L))
                .thenReturn(true);
        lenient().when(systemConfigService.getConfigForStatusAuthorization())
                .thenReturn(config(true));
        authenticate(manager);
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validatesNonNullReasonBeforeAnyRepositoryAccess() {
        PatchTaskStatusRequest request = request(TaskStatus.BLOCKED, "\u0000");

        assertThrows(IllegalArgumentException.class, () -> service.patch(20L, request));

        verifyNoInteractions(userRepository, taskRepository, entityManager, taskWorkflowService);
    }

    @Test
    void validatesOversizedReasonButAllowsOmittedReasonToReachAuthorization() {
        PatchTaskStatusRequest oversized = request(TaskStatus.BLOCKED, "x".repeat(4001));

        assertThrows(IllegalArgumentException.class, () -> service.patch(20L, oversized));
        verifyNoInteractions(userRepository, taskRepository, entityManager, taskWorkflowService);

        PatchTaskStatusRequest omitted = new PatchTaskStatusRequest();
        omitted.setStatus(TaskStatus.TODO);
        when(taskWorkflowService.evaluate(any())).thenReturn(new TaskWorkflowService.TaskTransitionDecision(
                TaskWorkflowService.TaskWorkflowActor.MANAGER,
                TaskStatus.TODO, TaskStatus.TODO, true, false, null, null));

        service.patch(20L, omitted);

        ArgumentCaptor<TaskWorkflowService.TaskTransitionContext> context =
                ArgumentCaptor.forClass(TaskWorkflowService.TaskTransitionContext.class);
        verify(taskWorkflowService).evaluate(context.capture());
        assertNull(context.getValue().rawBlockedReason());
    }

    @Test
    void preliminaryCapabilityDenialOccursBeforeTaskLock() {
        when(taskPermissionHelper.isManagerOfTaskProjectOrLab(eq(2L), same(initial)))
                .thenReturn(false);

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.patch(20L, request(TaskStatus.IN_PROGRESS, null)));

        verify(taskRepository, never()).findByIdForUpdate(anyLong());
        verifyNoInteractions(entityManager, taskWorkflowService, auditLogService, taskActivityRecorder);
    }

    @Test
    void lockedScopeDriftFailsBeforeFreshCapabilityResolution() {
        locked.setAssigneeId(99L);

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.patch(20L, request(TaskStatus.IN_PROGRESS, null)));

        verify(entityManager).clear();
        verify(taskRepository).findByIdForUpdate(20L);
        verify(userRepository, never()).findByIdForStatusAuthorization(anyLong());
        verifyNoInteractions(taskWorkflowService, auditLogService);
    }

    @Test
    void forwardsLockedStateAndInvokesEngineExactlyOnceThenAudits() {
        TaskActivityRecorder.TaskSnapshot before = snapshot(1);
        when(taskActivityRecorder.capture(same(locked))).thenReturn(before);
        User refreshedManager = user(2L, "manager-refreshed", "LAB_MANAGER");
        when(userRepository.findByIdForStatusAuthorization(2L)).thenReturn(Optional.of(refreshedManager));
        locked.setStatus(TaskStatus.NEEDS_REVISION);
        locked.setProgressPercent(7);
        PatchTaskStatusRequest request = request(TaskStatus.IN_PROGRESS, null);
        TaskWorkflowService.TaskTransitionDecision decision =
                new TaskWorkflowService.TaskTransitionDecision(
                        TaskWorkflowService.TaskWorkflowActor.MANAGER,
                        TaskStatus.NEEDS_REVISION, TaskStatus.IN_PROGRESS, false, false, null, 10);
        when(taskWorkflowService.evaluate(any())).thenReturn(decision);

        service.patch(20L, request);

        ArgumentCaptor<TaskWorkflowService.TaskTransitionContext> context =
                ArgumentCaptor.forClass(TaskWorkflowService.TaskTransitionContext.class);
        verify(taskWorkflowService).evaluate(context.capture());
        assertEquals(TaskStatus.NEEDS_REVISION, context.getValue().currentStatus());
        assertEquals(TaskStatus.IN_PROGRESS, context.getValue().targetStatus());
        assertEquals(7, context.getValue().currentProgressPercent());
        assertNull(context.getValue().rawBlockedReason());
        assertTrue(context.getValue().managerInScope());
        verify(entityManager).clear();
        verify(taskRepository).save(locked);
        verify(auditLogService).log(eq(refreshedManager), any(), any(), eq("RESEARCH_TASK"), eq(20L),
                eq("Updated research task status"), contains("\"progressChanged\":true"));
        verify(taskActivityRecorder).recordMutation(same(before), same(locked), same(refreshedManager));
        assertEquals(TaskStatus.IN_PROGRESS, locked.getStatus());
        assertEquals(10, locked.getProgressPercent());
        assertEquals(TaskStatus.TODO, initial.getStatus());
        assertEquals(0, initial.getProgressPercent());
    }

    @Test
    void pureSameStatusDecisionDoesNotMutateSaveOrAudit() {
        TaskWorkflowService.TaskTransitionDecision decision =
                new TaskWorkflowService.TaskTransitionDecision(
                        TaskWorkflowService.TaskWorkflowActor.MANAGER,
                        TaskStatus.TODO, TaskStatus.TODO, true, false, null, null);
        when(taskWorkflowService.evaluate(any())).thenReturn(decision);

        service.patch(20L, request(TaskStatus.TODO, null));

        verify(taskWorkflowService).evaluate(any());
        verify(taskRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
        verifyNoInteractions(taskActivityRecorder);
        assertEquals(TaskStatus.TODO, locked.getStatus());
        assertEquals(0, locked.getProgressPercent());
    }

    @Test
    void allInvisibleFormatReasonReachesEngineForSemanticValidation() {
        when(taskWorkflowService.evaluate(any()))
                .thenThrow(new IllegalArgumentException("Blocked reason is required"));

        assertThrows(IllegalArgumentException.class,
                () -> service.patch(20L, request(TaskStatus.BLOCKED, "\u200B\u202E")));

        verify(taskWorkflowService).evaluate(any());
        verify(taskRepository, never()).save(any());
    }

    @Test
    void lockedAuthorizationUsesCurrentReadsAfterTaskAndActorLocks() {
        when(taskWorkflowService.evaluate(any())).thenReturn(new TaskWorkflowService.TaskTransitionDecision(
                TaskWorkflowService.TaskWorkflowActor.MANAGER,
                TaskStatus.TODO, TaskStatus.IN_PROGRESS, false, false, null, 10));

        service.patch(20L, request(TaskStatus.IN_PROGRESS, null));

        InOrder order = inOrder(taskRepository, userRepository, taskPermissionHelper);
        order.verify(taskRepository).findByIdForUpdate(20L);
        order.verify(userRepository).findByIdForStatusAuthorization(2L);
        order.verify(taskPermissionHelper).resolveStatusAuthorizationScope(locked);
        order.verify(taskPermissionHelper)
                .isLaboratoryManagedByForStatusAuthorization(1L, 2L);
        verify(taskPermissionHelper, times(1))
                .isManagerOfTaskProjectOrLab(eq(2L), same(initial));
        verify(taskPermissionHelper, never()).isManagerOfTaskProjectOrLab(eq(2L), same(locked));
    }

    @Test
    void unexpectedNullConfigFailsClosedBeforeEngine() {
        when(systemConfigService.getConfigForStatusAuthorization()).thenReturn(null);

        assertThrows(IllegalStateException.class,
                () -> service.patch(20L, request(TaskStatus.IN_PROGRESS, null)));

        verifyNoInteractions(taskWorkflowService);
        verify(taskRepository, never()).save(any());
    }

    @Test
    void unexpectedNullResearchSectionFailsClosedBeforeEngine() {
        when(systemConfigService.getConfigForStatusAuthorization())
                .thenReturn(new SystemConfigResponse(null, null, null, null, null));

        assertThrows(IllegalStateException.class,
                () -> service.patch(20L, request(TaskStatus.IN_PROGRESS, null)));

        verifyNoInteractions(taskWorkflowService);
    }

    @Test
    void disabledDoneGateForwardsFalseWithoutReportRead() {
        TaskActivityRecorder.TaskSnapshot before = snapshot(2);
        when(taskActivityRecorder.capture(same(locked))).thenReturn(before);
        when(systemConfigService.getConfigForStatusAuthorization()).thenReturn(config(false));
        ArgumentCaptor<TaskWorkflowService.TaskTransitionContext> context =
                ArgumentCaptor.forClass(TaskWorkflowService.TaskTransitionContext.class);
        when(taskWorkflowService.evaluate(context.capture())).thenReturn(new TaskWorkflowService.TaskTransitionDecision(
                TaskWorkflowService.TaskWorkflowActor.MANAGER,
                TaskStatus.IN_REVIEW, TaskStatus.DONE, false, false, null, 100));
        locked.setStatus(TaskStatus.IN_REVIEW);

        service.patch(20L, request(TaskStatus.DONE, null));

        assertFalse(context.getValue().requireApprovedReport());
        assertFalse(context.getValue().hasApprovedReport());
        verify(reportRepository, never()).findLatestApprovedForStatusAuthorization(anyLong());
        verify(taskActivityRecorder).recordMutation(same(before), same(locked), same(manager));
    }

    @Test
    void blockedReasonRefreshPersistsAndAuditsWithoutChangingStatus() {
        locked.setStatus(TaskStatus.BLOCKED);
        when(taskWorkflowService.evaluate(any())).thenReturn(new TaskWorkflowService.TaskTransitionDecision(
                TaskWorkflowService.TaskWorkflowActor.MANAGER,
                TaskStatus.BLOCKED, TaskStatus.BLOCKED, true, true, "Needs equipment", null));

        service.patch(20L, request(TaskStatus.BLOCKED, "Needs equipment"));

        assertEquals(TaskStatus.BLOCKED, locked.getStatus());
        assertEquals("Needs equipment", locked.getBlockedReason());
        verify(taskRepository).save(locked);
        verify(auditLogService).log(eq(manager), any(), any(), eq("RESEARCH_TASK"), eq(20L),
                any(), contains("\"blockedReasonChanged\":true"));
    }

    @Test
    void blockedStatusForwardsCapturedSnapshotToRecorder() {
        TaskActivityRecorder.TaskSnapshot before = snapshot(3);
        when(taskActivityRecorder.capture(same(locked))).thenReturn(before);
        when(taskWorkflowService.evaluate(any())).thenReturn(new TaskWorkflowService.TaskTransitionDecision(
                TaskWorkflowService.TaskWorkflowActor.MANAGER,
                TaskStatus.TODO, TaskStatus.BLOCKED, false, true, "Needs equipment", null));

        service.patch(20L, request(TaskStatus.BLOCKED, "Needs equipment"));

        verify(taskActivityRecorder).recordMutation(same(before), same(locked), same(manager));
    }

    @Test
    void leavingBlockedClearsReasonAndReportsUnchangedProgressWithoutLeakingReason() {
        locked.setStatus(TaskStatus.BLOCKED);
        locked.setBlockedReason("sensitive blocker");
        locked.setProgressPercent(10);
        when(taskWorkflowService.evaluate(any())).thenReturn(new TaskWorkflowService.TaskTransitionDecision(
                TaskWorkflowService.TaskWorkflowActor.MANAGER,
                TaskStatus.BLOCKED, TaskStatus.IN_PROGRESS, false, true, null, 10));

        service.patch(20L, request(TaskStatus.IN_PROGRESS, null));

        assertEquals(TaskStatus.IN_PROGRESS, locked.getStatus());
        assertNull(locked.getBlockedReason());
        verify(auditLogService).log(eq(manager), any(), any(), eq("RESEARCH_TASK"), eq(20L),
                any(), argThat(metadata -> metadata.contains("\"progressChanged\":false")
                        && !metadata.contains("sensitive blocker")));
    }

    @Test
    void dualRoleActorKeepsLeaderCapabilityWhenManagerScopeIsAbsent() {
        manager.addRole(new Role("STUDENT", "Student"));
        initial.setGroupId(100L);
        locked.setGroupId(100L);
        when(taskPermissionHelper.isManagerOfTaskProjectOrLab(eq(2L), same(initial))).thenReturn(false);
        when(taskPermissionHelper.isLeaderInTaskGroup(eq(2L), same(initial))).thenReturn(true);
        when(taskPermissionHelper.isMemberInTaskGroup(eq(2L), same(initial))).thenReturn(true);
        when(taskPermissionHelper.resolveStatusAuthorizationScope(any(TaskEntity.class)))
                .thenReturn(new TaskPermissionHelper.StatusAuthorizationScope(true, 1L));
        when(taskPermissionHelper.isLaboratoryManagedByForStatusAuthorization(1L, 2L))
                .thenReturn(false);
        when(taskPermissionHelper.findGroupRoleForStatusAuthorization(2L, locked))
                .thenReturn(com.web.labportalbackend.research.enums.GroupRole.LEADER);
        when(taskWorkflowService.evaluate(any())).thenReturn(new TaskWorkflowService.TaskTransitionDecision(
                TaskWorkflowService.TaskWorkflowActor.LEADER,
                TaskStatus.TODO, TaskStatus.TODO, true, false, null, null));

        service.patch(20L, request(TaskStatus.TODO, null));

        ArgumentCaptor<TaskWorkflowService.TaskTransitionContext> context =
                ArgumentCaptor.forClass(TaskWorkflowService.TaskTransitionContext.class);
        verify(taskWorkflowService).evaluate(context.capture());
        assertFalse(context.getValue().managerInScope());
        assertTrue(context.getValue().leaderInScope());
        assertTrue(context.getValue().activeGroupMember());
        verify(taskRepository, never()).save(any());
    }

    @Test
    void dualRoleLockedReadsKeepTaskActorScopeAndMembershipOrder() {
        manager.addRole(new Role("STUDENT", "Student"));
        initial.setGroupId(100L);
        locked.setGroupId(100L);
        when(taskPermissionHelper.isLeaderInTaskGroup(eq(2L), same(initial))).thenReturn(true);
        when(taskPermissionHelper.isMemberInTaskGroup(eq(2L), same(initial))).thenReturn(true);
        when(taskPermissionHelper.findGroupRoleForStatusAuthorization(2L, locked))
                .thenReturn(com.web.labportalbackend.research.enums.GroupRole.LEADER);
        when(taskWorkflowService.evaluate(any())).thenReturn(new TaskWorkflowService.TaskTransitionDecision(
                TaskWorkflowService.TaskWorkflowActor.MANAGER,
                TaskStatus.TODO, TaskStatus.TODO, true, false, null, null));

        service.patch(20L, request(TaskStatus.TODO, null));

        InOrder order = inOrder(taskRepository, userRepository, taskPermissionHelper);
        order.verify(taskRepository).findByIdForUpdate(20L);
        order.verify(userRepository).findByIdForStatusAuthorization(2L);
        order.verify(taskPermissionHelper).resolveStatusAuthorizationScope(locked);
        order.verify(taskPermissionHelper)
                .isLaboratoryManagedByForStatusAuthorization(1L, 2L);
        order.verify(taskPermissionHelper).findGroupRoleForStatusAuthorization(2L, locked);
    }

    @Test
    void enabledDoneGateUsesCurrentApprovedReportRead() {
        locked.setStatus(TaskStatus.IN_REVIEW);
        when(reportRepository.findLatestApprovedForStatusAuthorization(20L))
                .thenReturn(List.of(new com.web.labportalbackend.research.entity.ReportEntity()));
        ArgumentCaptor<TaskWorkflowService.TaskTransitionContext> context =
                ArgumentCaptor.forClass(TaskWorkflowService.TaskTransitionContext.class);
        when(taskWorkflowService.evaluate(context.capture())).thenReturn(new TaskWorkflowService.TaskTransitionDecision(
                TaskWorkflowService.TaskWorkflowActor.MANAGER,
                TaskStatus.IN_REVIEW, TaskStatus.DONE, false, false, null, 100));

        service.patch(20L, request(TaskStatus.DONE, null));

        assertTrue(context.getValue().requireApprovedReport());
        assertTrue(context.getValue().hasApprovedReport());
        InOrder order = inOrder(
                taskRepository,
                userRepository,
                taskPermissionHelper,
                systemConfigService,
                reportRepository);
        order.verify(taskRepository).findByIdForUpdate(20L);
        order.verify(userRepository).findByIdForStatusAuthorization(2L);
        order.verify(taskPermissionHelper).resolveStatusAuthorizationScope(locked);
        order.verify(taskPermissionHelper)
                .isLaboratoryManagedByForStatusAuthorization(1L, 2L);
        order.verify(systemConfigService).getConfigForStatusAuthorization();
        order.verify(reportRepository).findLatestApprovedForStatusAuthorization(20L);
    }

    private SystemConfigResponse config(boolean requireApprovedReport) {
        return new SystemConfigResponse(
                null, null, null, null,
                new SystemConfigResponse.ResearchConfig(10, requireApprovedReport, true, true));
    }

    private PatchTaskStatusRequest request(TaskStatus status, String reason) {
        PatchTaskStatusRequest request = new PatchTaskStatusRequest();
        request.setStatus(status);
        request.setBlockedReason(reason);
        return request;
    }

    private TaskActivityRecorder.TaskSnapshot snapshot(int schemaVersion) {
        return new TaskActivityRecorder.TaskSnapshot(schemaVersion, null, null, null, null, null,
                "before-" + schemaVersion, null, TaskStatus.TODO, null, null, null, null, 0);
    }

    private TaskEntity task(TaskStatus status, int progress) {
        TaskEntity task = TaskEntity.builder().projectId(50L).status(status).progressPercent(progress)
                .title("Task").build();
        task.setId(20L);
        task.setActive(true);
        task.setDeleted(false);
        return task;
    }

    private User user(Long id, String username, String role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(username + "@example.test");
        user.setActive(true);
        user.setDeleted(false);
        user.addRole(new Role(role, role));
        return user;
    }

    private void authenticate(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getUsername(), null, List.of()));
    }
}
