package com.web.labportalbackend.research.service;

import com.web.labportalbackend.admin.audit.enums.AuditAction;
import com.web.labportalbackend.admin.audit.enums.AuditModule;
import com.web.labportalbackend.admin.audit.service.AuditLogService;
import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.dto.request.PatchResearchTaskRequest;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.MilestoneEntity;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.entity.TaskEntity;
import com.web.labportalbackend.research.enums.TaskPriority;
import com.web.labportalbackend.research.enums.TaskStatus;
import com.web.labportalbackend.research.enums.TaskType;
import com.web.labportalbackend.research.repository.GroupMemberRepository;
import com.web.labportalbackend.research.repository.GroupRepository;
import com.web.labportalbackend.research.repository.MilestoneRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.repository.TaskRepository;
import com.web.labportalbackend.research.service.impl.TaskMetadataPatchService;
import com.web.labportalbackend.research.service.impl.TaskActivityRecorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskMetadataPatchServiceTest {

    @Mock TaskRepository taskRepository;
    @Mock ProjectRepository projectRepository;
    @Mock GroupRepository groupRepository;
    @Mock MilestoneRepository milestoneRepository;
    @Mock UserRepository userRepository;
    @Mock GroupMemberRepository groupMemberRepository;
    @Mock LaboratoryRepository laboratoryRepository;
    @Mock AuditLogService auditLogService;
    @Mock TaskActivityRecorder taskActivityRecorder;
    @InjectMocks TaskMetadataPatchService service;

    private User manager;
    private Laboratory lab;
    private ProjectEntity project;
    private TaskEntity task;

    @BeforeEach
    void setUp() {
        manager = user(2L, "manager", "LAB_MANAGER");
        lab = lab(1L);
        project = project(50L, lab);
        task = task(20L, null);
        authenticate(manager);
        lenient().when(userRepository.findByUsername("manager")).thenReturn(Optional.of(manager));
        lenient().when(taskRepository.findByIdAndDeletedFalseAndActiveTrue(20L)).thenReturn(Optional.of(task));
        lenient().when(taskRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(task));
        lenient().when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(50L)).thenReturn(Optional.of(project));
        lenient().when(laboratoryRepository.existsByIdAndManagerIdAndActiveTrueAndDeletedFalse(1L, 2L)).thenReturn(true);
        lenient().when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void pureNormalizedNoOpDoesNotSaveAuditOrTouchTimestamp() {
        Instant updatedAt = Instant.parse("2026-07-01T00:00:00Z");
        task.setUpdatedAt(updatedAt);
        PatchResearchTaskRequest request = new PatchResearchTaskRequest();
        request.setTitle("  Original  ");

        assertEquals("Original", service.patch(20L, request).getTitle());

        assertEquals(updatedAt, task.getUpdatedAt());
        verify(taskRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
        verifyNoInteractions(taskActivityRecorder);
    }

    @Test
    void mixedPatchAppliesOncePreservesServerFieldsAndAuditsStableActualOrder() {
        TaskActivityRecorder.TaskSnapshot before = snapshot(1);
        when(taskActivityRecorder.capture(same(task))).thenReturn(before);
        task.setMilestoneId(10L);
        task.setDescription("old");
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setProgressPercent(40);
        task.setBlockedReason("blocked");
        task.setCreatedBy(9L);
        task.setDueDate(LocalDate.of(2026, 8, 1));
        task.setDeadline(LocalDate.of(2026, 8, 1));
        MilestoneEntity milestone = milestone(10L, project, null);
        when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(10L)).thenReturn(Optional.of(milestone));
        PatchResearchTaskRequest request = new PatchResearchTaskRequest();
        request.setTitle(" Original ");
        request.setDescription("new");
        request.setPriority(TaskPriority.HIGH);
        request.setDueDate(LocalDate.of(2026, 9, 2));

        service.patch(20L, request);

        assertEquals("new", task.getDescription());
        assertEquals(TaskPriority.HIGH, task.getPriority());
        assertEquals(LocalDate.of(2026, 9, 2), task.getDueDate());
        assertEquals(task.getDueDate(), task.getDeadline());
        assertEquals(TaskStatus.IN_PROGRESS, task.getStatus());
        assertEquals(40, task.getProgressPercent());
        assertEquals("blocked", task.getBlockedReason());
        assertEquals(9L, task.getCreatedBy());
        verify(taskRepository).save(task);
        ArgumentCaptor<String> metadata = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).log(eq(manager), eq(AuditAction.UPDATE_RESEARCH_TASK),
                eq(AuditModule.RESEARCH), eq("RESEARCH_TASK"), eq(20L), anyString(), metadata.capture());
        assertEquals("{\"changedFields\":[\"description\",\"priority\",\"dueDate\"]}", metadata.getValue());
        verify(taskActivityRecorder).recordMutation(same(before), same(task), same(manager));
    }

    @Test
    void milestoneCanBeClearedWithoutChangingStatus() {
        task.setMilestoneId(10L);
        task.setStatus(TaskStatus.TODO);
        PatchResearchTaskRequest request = new PatchResearchTaskRequest();
        request.setMilestoneId(null);

        service.patch(20L, request);

        assertNull(task.getMilestoneId());
        assertEquals(TaskStatus.TODO, task.getStatus());
    }

    @Test
    void explicitNullRequiredMetadataIsRejectedBeforePersistence() {
        PatchResearchTaskRequest nullTitle = new PatchResearchTaskRequest();
        nullTitle.setTitle(null);
        PatchResearchTaskRequest nullPriority = new PatchResearchTaskRequest();
        nullPriority.setPriority(null);
        PatchResearchTaskRequest nullType = new PatchResearchTaskRequest();
        nullType.setType(null);

        assertThrows(IllegalArgumentException.class, () -> service.patch(20L, nullTitle));
        assertThrows(IllegalArgumentException.class, () -> service.patch(20L, nullPriority));
        assertThrows(IllegalArgumentException.class, () -> service.patch(20L, nullType));
        verify(taskRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
        verifyNoInteractions(taskActivityRecorder);
    }

    @Test
    void explicitNullDueDateClearsCanonicalAndLegacyFields() {
        TaskActivityRecorder.TaskSnapshot before = snapshot(2);
        when(taskActivityRecorder.capture(same(task))).thenReturn(before);
        task.setDueDate(LocalDate.of(2026, 8, 1));
        task.setDeadline(LocalDate.of(2026, 8, 1));
        PatchResearchTaskRequest request = new PatchResearchTaskRequest();
        request.setDueDate(null);

        service.patch(20L, request);

        assertNull(task.getDueDate());
        assertNull(task.getDeadline());
        verify(auditLogService).log(any(), any(), any(), any(), any(), any(),
                eq("{\"changedFields\":[\"dueDate\"]}"));
        verify(taskActivityRecorder).recordMutation(same(before), same(task), same(manager));
    }

    @Test
    void groupChangeValidatesRetainedAssigneeAgainstFinalGroupBeforeMutation() {
        GroupEntity groupB = group(101L, project, lab);
        task.setGroupId(100L);
        task.setAssigneeId(7L);
        User assignee = user(7L, "member", "STUDENT");
        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(101L)).thenReturn(Optional.of(groupB));
        when(userRepository.findById(7L)).thenReturn(Optional.of(assignee));
        when(groupMemberRepository.existsByGroupIdAndUserIdAndActiveTrueAndDeletedFalse(101L, 7L)).thenReturn(false);
        PatchResearchTaskRequest request = new PatchResearchTaskRequest();
        request.setGroupId(101L);

        assertThrows(IllegalArgumentException.class, () -> service.patch(20L, request));

        assertEquals(100L, task.getGroupId());
        verify(taskRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
        verifyNoInteractions(taskActivityRecorder);
    }

    @Test
    void clearingGroupRequiresBacklogButAlreadyNullExplicitNullIsNoOp() {
        task.setGroupId(100L);
        task.setStatus(TaskStatus.TODO);
        PatchResearchTaskRequest clear = new PatchResearchTaskRequest();
        clear.setGroupId(null);

        assertThrows(IllegalArgumentException.class, () -> service.patch(20L, clear));
        assertEquals(100L, task.getGroupId());

        task.setGroupId(null);
        service.patch(20L, clear);
        verify(taskRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void managerCanClearBacklogGroupWhenDependentRelationsAreClearedTogether() {
        task.setGroupId(100L);
        task.setMilestoneId(10L);
        task.setParentTaskId(5L);
        task.setAssigneeId(7L);
        task.setStatus(TaskStatus.BACKLOG);
        PatchResearchTaskRequest request = new PatchResearchTaskRequest();
        request.setGroupId(null);
        request.setMilestoneId(null);
        request.setParentTaskId(null);
        request.setAssigneeId(null);

        service.patch(20L, request);

        assertNull(task.getGroupId());
        assertNull(task.getMilestoneId());
        assertNull(task.getParentTaskId());
        assertNull(task.getAssigneeId());
        verify(auditLogService).log(any(), any(), any(), any(), any(), any(),
                eq("{\"changedFields\":[\"groupId\",\"milestoneId\",\"parentTaskId\",\"assigneeId\"]}"));
    }

    @Test
    void rejectsMissingOrInvalidFinalReferences() {
        PatchResearchTaskRequest missingGroup = new PatchResearchTaskRequest();
        missingGroup.setGroupId(100L);
        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(100L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.patch(20L, missingGroup));

        PatchResearchTaskRequest missingMilestone = new PatchResearchTaskRequest();
        missingMilestone.setMilestoneId(10L);
        when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(10L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.patch(20L, missingMilestone));

        GroupEntity group = group(100L, project, lab);
        PatchResearchTaskRequest missingAssignee = new PatchResearchTaskRequest();
        missingAssignee.setGroupId(100L);
        missingAssignee.setAssigneeId(7L);
        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(100L)).thenReturn(Optional.of(group));
        when(userRepository.findById(7L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.patch(20L, missingAssignee));

        verify(taskRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void rejectsMilestoneWhoseProjectOrGroupDoesNotMatchFinalScope() {
        GroupEntity group = group(100L, project, lab);
        GroupEntity otherGroup = group(101L, project, lab);
        task.setGroupId(100L);
        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(100L)).thenReturn(Optional.of(group));
        MilestoneEntity scoped = milestone(10L, project, otherGroup);
        when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(10L)).thenReturn(Optional.of(scoped));
        PatchResearchTaskRequest request = new PatchResearchTaskRequest();
        request.setMilestoneId(10L);

        assertThrows(IllegalArgumentException.class, () -> service.patch(20L, request));
        verify(taskRepository, never()).save(any());
    }

    @Test
    void explicitAssigneeUpdatesMapperAssociationAndRequiresMembership() {
        TaskActivityRecorder.TaskSnapshot before = snapshot(3);
        when(taskActivityRecorder.capture(same(task))).thenReturn(before);
        GroupEntity group = group(100L, project, lab);
        User assignee = user(7L, "member", "ADMIN");
        task.setGroupId(100L);
        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(100L)).thenReturn(Optional.of(group));
        when(userRepository.findById(7L)).thenReturn(Optional.of(assignee));
        when(groupMemberRepository.existsByGroupIdAndUserIdAndActiveTrueAndDeletedFalse(100L, 7L)).thenReturn(true);
        PatchResearchTaskRequest request = new PatchResearchTaskRequest();
        request.setAssigneeId(7L);

        assertEquals("member@example.test", service.patch(20L, request).getAssignedToStudentEmail());
        assertSame(assignee, task.getAssignedToStudent());
        verify(taskActivityRecorder).recordMutation(same(before), same(task), same(manager));
    }

    private TaskActivityRecorder.TaskSnapshot snapshot(int schemaVersion) {
        return new TaskActivityRecorder.TaskSnapshot(schemaVersion, null, null, null, null, null,
                "before-" + schemaVersion, null, TaskStatus.TODO, TaskPriority.MEDIUM, TaskType.TASK,
                null, null, 0);
    }

    private void authenticate(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getUsername(), null, List.of()));
    }

    private User user(Long id, String username, String role) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(username + "@example.test");
        user.addRole(new Role(role, role));
        return user;
    }

    private Laboratory lab(Long id) {
        Laboratory laboratory = new Laboratory();
        laboratory.setId(id);
        return laboratory;
    }

    private ProjectEntity project(Long id, Laboratory laboratory) {
        ProjectEntity entity = ProjectEntity.builder().lab(laboratory).title("Project").build();
        entity.setId(id);
        return entity;
    }

    private GroupEntity group(Long id, ProjectEntity groupProject, Laboratory laboratory) {
        GroupEntity entity = GroupEntity.builder().project(groupProject).lab(laboratory).name("Group").build();
        entity.setId(id);
        return entity;
    }

    private MilestoneEntity milestone(Long id, ProjectEntity milestoneProject, GroupEntity group) {
        MilestoneEntity entity = MilestoneEntity.builder().project(milestoneProject).group(group).title("M").build();
        entity.setId(id);
        return entity;
    }

    private TaskEntity task(Long id, Long groupId) {
        TaskEntity entity = TaskEntity.builder()
                .projectId(50L)
                .groupId(groupId)
                .title("Original")
                .status(TaskStatus.BACKLOG)
                .priority(TaskPriority.MEDIUM)
                .type(TaskType.TASK)
                .progressPercent(0)
                .build();
        entity.setId(id);
        return entity;
    }
}
