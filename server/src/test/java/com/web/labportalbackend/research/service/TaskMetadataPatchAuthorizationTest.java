package com.web.labportalbackend.research.service;

import com.web.labportalbackend.admin.audit.service.AuditLogService;
import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.dto.request.PatchResearchTaskRequest;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.MilestoneEntity;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.entity.TaskEntity;
import com.web.labportalbackend.research.enums.GroupRole;
import com.web.labportalbackend.research.enums.TaskPriority;
import com.web.labportalbackend.research.enums.TaskStatus;
import com.web.labportalbackend.research.enums.TaskType;
import com.web.labportalbackend.research.repository.GroupMemberRepository;
import com.web.labportalbackend.research.repository.GroupRepository;
import com.web.labportalbackend.research.repository.MilestoneRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.repository.TaskRepository;
import com.web.labportalbackend.research.service.impl.TaskMetadataPatchService;
import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskMetadataPatchAuthorizationTest {

    @Mock TaskRepository taskRepository;
    @Mock ProjectRepository projectRepository;
    @Mock GroupRepository groupRepository;
    @Mock MilestoneRepository milestoneRepository;
    @Mock UserRepository userRepository;
    @Mock GroupMemberRepository groupMemberRepository;
    @Mock LaboratoryRepository laboratoryRepository;
    @Mock AuditLogService auditLogService;
    @InjectMocks TaskMetadataPatchService service;

    private User manager;
    private ProjectEntity project;
    private TaskEntity task;

    @BeforeEach
    void setUp() {
        manager = user(2L, "manager", "LAB_MANAGER");
        Laboratory lab = lab(1L);
        project = project(50L, lab);
        task = task(20L, 50L, null);
        authenticate(manager);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void unknownAndEmptyRequestsFailBeforeActorOrTaskLookup() {
        PatchResearchTaskRequest unknown = new PatchResearchTaskRequest();
        unknown.captureUnknownProperty("status", null);
        PatchResearchTaskRequest empty = new PatchResearchTaskRequest();

        assertThrows(IllegalArgumentException.class, () -> service.patch(20L, unknown));
        assertThrows(IllegalArgumentException.class, () -> service.patch(20L, empty));

        verifyNoInteractions(userRepository, taskRepository, projectRepository, laboratoryRepository, auditLogService);
    }

    @Test
    void wrongLabManagerIsDeniedBeforeLockedLookup() {
        stubCurrentUser(manager);
        when(taskRepository.findByIdAndDeletedFalseAndActiveTrue(20L)).thenReturn(Optional.of(task));
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(50L)).thenReturn(Optional.of(project));
        when(laboratoryRepository.existsByIdAndManagerIdAndActiveTrueAndDeletedFalse(1L, 2L)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.patch(20L, title("Changed")));

        verify(taskRepository, never()).findByIdForUpdate(20L);
        verify(taskRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void inactiveManagerAndInactiveProjectAreRejectedBeforeLock() {
        manager.setActive(false);
        stubCurrentUser(manager);
        assertThrows(AccessDeniedException.class, () -> service.patch(20L, title("Changed")));
        verify(taskRepository, never()).findByIdAndDeletedFalseAndActiveTrue(any());

        manager.setActive(true);
        when(taskRepository.findByIdAndDeletedFalseAndActiveTrue(20L)).thenReturn(Optional.of(task));
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(50L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.patch(20L, title("Changed")));
        verify(taskRepository, never()).findByIdForUpdate(20L);
    }

    @Test
    void ordinaryMemberIsDeniedBeforeLockedLookupEvenWhenAssigned() {
        User member = user(7L, "member", "STUDENT");
        TaskEntity groupTask = task(20L, 50L, 100L);
        groupTask.setAssigneeId(member.getId());
        authenticate(member);
        stubCurrentUser(member);
        when(taskRepository.findByIdAndDeletedFalseAndActiveTrue(20L)).thenReturn(Optional.of(groupTask));
        when(groupMemberRepository.findActiveRoleByGroupIdAndUserId(100L, 7L))
                .thenReturn(Optional.of(GroupRole.MEMBER));

        assertThrows(AccessDeniedException.class, () -> service.patch(20L, title("Changed")));

        verify(taskRepository, never()).findByIdForUpdate(20L);
        verifyNoInteractions(auditLogService);
    }

    @Test
    void projectLevelTaskHasNoLeaderUpdateScope() {
        User student = user(7L, "student", "STUDENT");
        authenticate(student);
        stubCurrentUser(student);
        when(taskRepository.findByIdAndDeletedFalseAndActiveTrue(20L)).thenReturn(Optional.of(task));

        assertThrows(AccessDeniedException.class, () -> service.patch(20L, title("Changed")));

        verify(taskRepository, never()).findByIdForUpdate(20L);
    }

    @Test
    void authorizedManagerUsesExactLabPermissionAndLockedLookup() {
        stubAuthorizedManager(task, project);
        when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals("Changed", service.patch(20L, title(" Changed ")).getTitle());

        verify(laboratoryRepository, times(2)).existsByIdAndManagerIdAndActiveTrueAndDeletedFalse(1L, 2L);
        verify(taskRepository).findByIdForUpdate(20L);
        verify(taskRepository).save(task);
    }

    @Test
    void managerWithMultipleLabsStillAuthorizesByTargetLabId() {
        stubAuthorizedManager(task, project);
        when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.patch(20L, title("Changed"));

        verify(laboratoryRepository, times(2))
                .existsByIdAndManagerIdAndActiveTrueAndDeletedFalse(1L, 2L);
        verify(laboratoryRepository, never())
                .existsByIdAndManagerIdAndActiveTrueAndDeletedFalse(2L, 2L);
    }

    @Test
    void activeLeaderCanPatchAllowedFieldButAnyGroupIdPresenceIsForbidden() {
        User leader = user(7L, "leader", "STUDENT");
        TaskEntity groupTask = task(20L, 50L, 100L);
        authenticate(leader);
        stubCurrentUser(leader);
        when(taskRepository.findByIdAndDeletedFalseAndActiveTrue(20L)).thenReturn(Optional.of(groupTask));
        when(taskRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(groupTask));
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(50L)).thenReturn(Optional.of(project));
        when(groupMemberRepository.findActiveRoleByGroupIdAndUserId(100L, 7L))
                .thenReturn(Optional.of(GroupRole.LEADER));
        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(100L))
                .thenReturn(Optional.of(group(100L, project, project.getLab())));
        when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals("Changed", service.patch(20L, title("Changed")).getTitle());

        PatchResearchTaskRequest forbidden = new PatchResearchTaskRequest();
        forbidden.setGroupId(100L);
        assertThrows(AccessDeniedException.class, () -> service.patch(20L, forbidden));
        verify(taskRepository, times(2)).findByIdForUpdate(20L);
    }

    @Test
    void activeLeaderCanPatchEveryAllowedMetadataFieldWhileGroupStaysFixed() {
        User leader = user(7L, "leader-all", "STUDENT");
        User assignee = user(8L, "assignee", "ADMIN");
        TaskEntity groupTask = task(20L, 50L, 100L);
        TaskEntity parent = task(5L, 50L, 100L);
        GroupEntity group = group(100L, project, project.getLab());
        MilestoneEntity milestone = MilestoneEntity.builder().project(project).group(group).title("M").build();
        milestone.setId(10L);
        authenticate(leader);
        stubCurrentUser(leader);
        when(taskRepository.findByIdAndDeletedFalseAndActiveTrue(20L)).thenReturn(Optional.of(groupTask));
        when(taskRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(groupTask));
        when(taskRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(parent));
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(50L)).thenReturn(Optional.of(project));
        when(groupMemberRepository.findActiveRoleByGroupIdAndUserId(100L, 7L))
                .thenReturn(Optional.of(GroupRole.LEADER));
        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(100L)).thenReturn(Optional.of(group));
        when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(10L)).thenReturn(Optional.of(milestone));
        when(userRepository.findById(8L)).thenReturn(Optional.of(assignee));
        when(groupMemberRepository.existsByGroupIdAndUserIdAndActiveTrueAndDeletedFalse(100L, 8L))
                .thenReturn(true);
        when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PatchResearchTaskRequest request = new PatchResearchTaskRequest();
        request.setMilestoneId(10L);
        request.setParentTaskId(5L);
        request.setTitle("Updated");
        request.setDescription("Description");
        request.setAssigneeId(8L);
        request.setPriority(TaskPriority.HIGH);
        request.setType(TaskType.REVIEW);
        request.setDueDate(java.time.LocalDate.of(2026, 10, 1));

        var response = service.patch(20L, request);

        assertEquals(100L, response.getGroupId());
        assertEquals(10L, response.getMilestoneId());
        assertEquals(5L, response.getParentTaskId());
        assertEquals("Updated", response.getTitle());
        assertEquals("Description", response.getDescription());
        assertEquals(8L, response.getAssignedToStudentId());
        assertEquals(TaskPriority.HIGH, response.getPriority());
        assertEquals(TaskType.REVIEW, response.getType());
        assertEquals(java.time.LocalDate.of(2026, 10, 1), response.getDueDate());
    }

    @Test
    void leaderOutsideGroupOrWithInactiveMembershipIsDeniedBeforeLock() {
        User leader = user(7L, "leader", "STUDENT");
        TaskEntity groupTask = task(20L, 50L, 100L);
        authenticate(leader);
        stubCurrentUser(leader);
        when(taskRepository.findByIdAndDeletedFalseAndActiveTrue(20L)).thenReturn(Optional.of(groupTask));
        when(groupMemberRepository.findActiveRoleByGroupIdAndUserId(100L, 7L)).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () -> service.patch(20L, title("Changed")));

        verify(taskRepository, never()).findByIdForUpdate(20L);
    }

    @Test
    void lockedReloadRejectsManagerWhenTaskScopeChanges() {
        ProjectEntity otherProject = project(60L, lab(2L));
        TaskEntity moved = task(20L, 60L, null);
        stubCurrentUser(manager);
        when(taskRepository.findByIdAndDeletedFalseAndActiveTrue(20L)).thenReturn(Optional.of(task));
        when(taskRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(moved));
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(50L)).thenReturn(Optional.of(project));
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(60L)).thenReturn(Optional.of(otherProject));
        when(laboratoryRepository.existsByIdAndManagerIdAndActiveTrueAndDeletedFalse(1L, 2L)).thenReturn(true);
        when(laboratoryRepository.existsByIdAndManagerIdAndActiveTrueAndDeletedFalse(2L, 2L)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.patch(20L, title("Changed")));

        verify(taskRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void lockedReloadRejectsLeaderWhoseMembershipWasLost() {
        User leader = user(7L, "leader", "STUDENT");
        TaskEntity groupTask = task(20L, 50L, 100L);
        authenticate(leader);
        stubCurrentUser(leader);
        when(taskRepository.findByIdAndDeletedFalseAndActiveTrue(20L)).thenReturn(Optional.of(groupTask));
        when(taskRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(groupTask));
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(50L)).thenReturn(Optional.of(project));
        when(groupMemberRepository.findActiveRoleByGroupIdAndUserId(100L, 7L))
                .thenReturn(Optional.of(GroupRole.LEADER), Optional.empty());

        assertThrows(AccessDeniedException.class, () -> service.patch(20L, title("Changed")));

        verify(taskRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    private void stubAuthorizedManager(TaskEntity currentTask, ProjectEntity currentProject) {
        stubCurrentUser(manager);
        when(taskRepository.findByIdAndDeletedFalseAndActiveTrue(20L)).thenReturn(Optional.of(currentTask));
        when(taskRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(currentTask));
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(currentProject.getId()))
                .thenReturn(Optional.of(currentProject));
        when(laboratoryRepository.existsByIdAndManagerIdAndActiveTrueAndDeletedFalse(
                currentProject.getLab().getId(), manager.getId())).thenReturn(true);
    }

    private void stubCurrentUser(User user) {
        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));
    }

    private PatchResearchTaskRequest title(String value) {
        PatchResearchTaskRequest request = new PatchResearchTaskRequest();
        request.setTitle(value);
        return request;
    }

    private void authenticate(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getUsername(), null, List.of()));
    }

    private User user(Long id, String username, String roleName) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(username + "@example.test");
        user.addRole(new Role(roleName, roleName));
        return user;
    }

    private Laboratory lab(Long id) {
        Laboratory lab = new Laboratory();
        lab.setId(id);
        return lab;
    }

    private ProjectEntity project(Long id, Laboratory lab) {
        ProjectEntity project = ProjectEntity.builder().lab(lab).title("Project").build();
        project.setId(id);
        return project;
    }

    private GroupEntity group(Long id, ProjectEntity project, Laboratory lab) {
        GroupEntity group = GroupEntity.builder().project(project).lab(lab).name("Group").build();
        group.setId(id);
        return group;
    }

    private TaskEntity task(Long id, Long projectId, Long groupId) {
        TaskEntity task = TaskEntity.builder()
                .projectId(projectId)
                .groupId(groupId)
                .title("Original")
                .status(TaskStatus.BACKLOG)
                .priority(TaskPriority.MEDIUM)
                .type(TaskType.TASK)
                .progressPercent(0)
                .build();
        task.setId(id);
        return task;
    }
}
