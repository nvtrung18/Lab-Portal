package com.web.labportalbackend.research.service;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.research.dto.response.ProjectTaskBoardResponse;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.TaskEntity;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.research.enums.GroupRole;
import com.web.labportalbackend.research.enums.TaskPriority;
import com.web.labportalbackend.research.enums.TaskStatus;
import com.web.labportalbackend.research.enums.TaskType;
import com.web.labportalbackend.research.repository.GroupMemberRepository;
import com.web.labportalbackend.research.repository.GroupRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.repository.TaskRepository;
import com.web.labportalbackend.research.security.TaskPermissionHelper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class TaskBoardReadServiceTest {
    @Mock TaskRepository taskRepository;
    @Mock ProjectRepository projectRepository;
    @Mock GroupRepository groupRepository;
    @Mock GroupMemberRepository groupMemberRepository;
    @Mock UserRepository userRepository;
    @Mock TaskPermissionHelper permissionHelper;
    @Mock Authentication authentication;
    @InjectMocks TaskBoardReadService service;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void defaultColumnsAreCanonicalAndOrderedIncludingEmptyColumns() {
        authenticate("student");
        User user = activeUser(7L);
        ProjectEntity project = ProjectEntity.builder().title("P").build();
        project.setId(50L);
        when(userRepository.findByUsername("student")).thenReturn(Optional.of(user));
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(50L)).thenReturn(Optional.of(project));
        when(permissionHelper.canViewProjectBoard(7L, project)).thenReturn(true);
        when(groupMemberRepository.findActiveGroupIdsByProjectIdAndUserIdAndRole(50L, 7L, com.web.labportalbackend.research.enums.GroupRole.LEADER)).thenReturn(List.of(100L));
        when(groupMemberRepository.findActiveGroupIdsByProjectIdAndUserIdAndRole(50L, 7L, com.web.labportalbackend.research.enums.GroupRole.MEMBER)).thenReturn(List.of());
        when(taskRepository.findBoardTasksForStudent(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(List.of());

        ProjectTaskBoardResponse response = service.read(50L, null, null, null, null, null, false, false);

        assertEquals(List.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.IN_REVIEW, TaskStatus.NEEDS_REVISION, TaskStatus.DONE, TaskStatus.BLOCKED),
                response.getColumns().stream().map(c -> c.getStatus()).toList());
        verify(userRepository).findByUsername("student");
    }

    @Test
    void explicitStatusIgnoresColumnFlags() {
        authenticate("student");
        User user = activeUser(7L);
        ProjectEntity project = ProjectEntity.builder().title("P").build();
        project.setId(50L);
        when(userRepository.findByUsername("student")).thenReturn(Optional.of(user));
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(50L)).thenReturn(Optional.of(project));
        when(permissionHelper.canViewProjectBoard(7L, project)).thenReturn(true);
        when(groupMemberRepository.findActiveGroupIdsByProjectIdAndUserIdAndRole(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(taskRepository.findBoardTasksForStudent(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(List.of());

        ProjectTaskBoardResponse response = service.read(50L, null, null, TaskStatus.BACKLOG, null, null, false, true);

        assertEquals(List.of(TaskStatus.BACKLOG), response.getColumns().stream().map(c -> c.getStatus()).toList());
    }

    @Test
    void inactiveProjectIsNotFoundBeforeCurrentUserResolutionOrBoardDependencies() {
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(50L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.read(50L, null, null, null, null, null, false, false));

        verify(projectRepository).findByIdAndDeletedFalseAndActiveTrue(50L);
        verifyNoInteractions(userRepository, permissionHelper, groupRepository, groupMemberRepository, taskRepository);
    }

    @Test
    void managerUsesProjectScopedQueryAndCannotReadAnotherLabProject() {
        authenticate("manager");
        User manager = activeUser(2L);
        manager.addRole(new Role("LAB_MANAGER", "manager"));
        ProjectEntity project = project(50L, 1L);
        when(userRepository.findByUsername("manager")).thenReturn(Optional.of(manager));
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(50L)).thenReturn(Optional.of(project));
        when(permissionHelper.canViewProjectBoard(2L, project)).thenReturn(true);
        when(taskRepository.findBoardTasksForManager(50L, null, null, null, null, null, false, false))
                .thenReturn(List.of());

        service.read(50L, null, null, null, null, null, false, false);

        verify(taskRepository).findBoardTasksForManager(50L, null, null, null, null, null, false, false);
        verify(taskRepository, never()).findBoardTasksForStudent(anyLong(), anyList(), anyList(), anyLong(),
                any(), any(), any(), any(), any(), anyBoolean(), anyBoolean());

        when(permissionHelper.canViewProjectBoard(2L, project)).thenReturn(false);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.read(50L, null, null, null, null, null, false, false));
    }

    @Test
    void mixedMembershipUsesLeaderAndAssignedMemberUnionScope() {
        ProjectEntity project = setupStudent(List.of(100L), List.of(200L));
        when(taskRepository.findBoardTasksForStudent(50L, List.of(100L), List.of(200L), 7L,
                null, null, null, null, null, false, false)).thenReturn(List.of());

        service.read(project.getId(), null, null, null, null, null, false, false);

        verify(taskRepository).findBoardTasksForStudent(50L, List.of(100L), List.of(200L), 7L,
                null, null, null, null, null, false, false);
    }

    @Test
    void allColumnFlagCombinationsAndExplicitStatusesRemainBinding() {
        setupStudent(List.of(100L), List.of());
        when(taskRepository.findBoardTasksForStudent(anyLong(), anyList(), anyList(), anyLong(),
                isNull(), isNull(), any(), isNull(), isNull(), anyBoolean(), anyBoolean())).thenReturn(List.of());

        assertEquals(List.of(TaskStatus.BACKLOG, TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.IN_REVIEW,
                        TaskStatus.NEEDS_REVISION, TaskStatus.DONE, TaskStatus.BLOCKED),
                statuses(service.read(50L, null, null, null, null, null, true, false)));
        assertEquals(List.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.IN_REVIEW,
                        TaskStatus.NEEDS_REVISION, TaskStatus.DONE, TaskStatus.BLOCKED, TaskStatus.CANCELLED),
                statuses(service.read(50L, null, null, null, null, null, false, true)));
        assertEquals(List.of(TaskStatus.BACKLOG, TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.IN_REVIEW,
                        TaskStatus.NEEDS_REVISION, TaskStatus.DONE, TaskStatus.BLOCKED, TaskStatus.CANCELLED),
                statuses(service.read(50L, null, null, null, null, null, true, true)));
        assertEquals(List.of(TaskStatus.TODO),
                statuses(service.read(50L, null, null, TaskStatus.TODO, null, null, true, true)));
        assertEquals(List.of(TaskStatus.BACKLOG),
                statuses(service.read(50L, null, null, TaskStatus.BACKLOG, null, null, false, false)));
        assertEquals(List.of(TaskStatus.CANCELLED),
                statuses(service.read(50L, null, null, TaskStatus.CANCELLED, null, null, false, false)));
    }

    @Test
    void combinedFiltersArePassedWithoutWideningVisibility() {
        setupStudent(List.of(100L), List.of(200L));
        GroupEntity group = group(100L, project(50L, 1L), 1L);
        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(100L)).thenReturn(Optional.of(group));
        when(taskRepository.findBoardTasksForStudent(50L, List.of(100L), List.of(200L), 7L,
                100L, null, null, TaskPriority.HIGH, TaskType.REVIEW, false, false)).thenReturn(List.of());

        service.read(50L, 100L, null, null, TaskPriority.HIGH, TaskType.REVIEW, false, false);

        verify(taskRepository).findBoardTasksForStudent(50L, List.of(100L), List.of(200L), 7L,
                100L, null, null, TaskPriority.HIGH, TaskType.REVIEW, false, false);
    }

    @Test
    void assigneeAndStatusFiltersAreValidatedAndComposed() {
        setupStudent(List.of(100L), List.of(200L));
        when(userRepository.findById(8L)).thenReturn(Optional.of(activeUser(8L)));
        when(groupMemberRepository.findActiveGroupIdsByProjectIdAndUserIdAndRole(50L, 8L, GroupRole.LEADER))
                .thenReturn(List.of(100L));
        when(groupMemberRepository.findActiveGroupIdsByProjectIdAndUserIdAndRole(50L, 8L, GroupRole.MEMBER))
                .thenReturn(List.of());
        when(taskRepository.findBoardTasksForStudent(50L, List.of(100L), List.of(200L), 7L,
                null, 8L, TaskStatus.IN_REVIEW, null, null, false, false)).thenReturn(List.of());

        service.read(50L, null, 8L, TaskStatus.IN_REVIEW, null, null, true, true);

        verify(taskRepository).findBoardTasksForStudent(50L, List.of(100L), List.of(200L), 7L,
                null, 8L, TaskStatus.IN_REVIEW, null, null, false, false);
    }

    @Test
    void missingAndOutOfScopeFilterResourcesUseNotFoundAndAccessDenied() {
        ProjectEntity project = setupStudent(List.of(100L), List.of(200L));
        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.read(50L, 999L, null, null, null, null, false, false));

        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(300L))
                .thenReturn(Optional.of(group(300L, project(60L, 1L), 1L)));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.read(50L, 300L, null, null, null, null, false, false));

        when(userRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.read(50L, null, 999L, null, null, null, false, false));

        when(userRepository.findById(8L)).thenReturn(Optional.of(activeUser(8L)));
        when(groupMemberRepository.findActiveGroupIdsByProjectIdAndUserIdAndRole(50L, 8L, GroupRole.LEADER))
                .thenReturn(List.of(300L));
        when(groupMemberRepository.findActiveGroupIdsByProjectIdAndUserIdAndRole(50L, 8L, GroupRole.MEMBER))
                .thenReturn(List.of());
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.read(50L, null, 8L, null, null, null, false, false));
        verify(taskRepository, never()).findBoardTasksForStudent(anyLong(), anyList(), anyList(), anyLong(),
                any(), any(), any(), any(), any(), anyBoolean(), anyBoolean());
        verify(groupMemberRepository, never()).findActiveGroupIdsByProjectIdAndUserId(anyLong(), anyLong());
    }

    @Test
    void managerCanFilterActiveMemberAssignee() {
        setupManager();
        when(userRepository.findById(8L)).thenReturn(Optional.of(activeUser(8L)));
        strictAssigneeGroups(8L, List.of(), List.of(100L));
        when(taskRepository.findBoardTasksForManager(50L, null, 8L, null, null, null, false, false))
                .thenReturn(List.of());

        service.read(50L, null, 8L, null, null, null, false, false);

        verify(taskRepository).findBoardTasksForManager(50L, null, 8L, null, null, null, false, false);
    }

    @Test
    void managerCanFilterActiveLeaderAssignee() {
        setupManager();
        when(userRepository.findById(8L)).thenReturn(Optional.of(activeUser(8L)));
        strictAssigneeGroups(8L, List.of(100L), List.of());
        when(taskRepository.findBoardTasksForManager(50L, null, 8L, null, null, null, false, false))
                .thenReturn(List.of());

        service.read(50L, null, 8L, null, null, null, false, false);

        verify(taskRepository).findBoardTasksForManager(50L, null, 8L, null, null, null, false, false);
    }

    @Test
    void managerRejectsExistingAssigneeWithoutStrictProjectScopeBeforeBoardQuery() {
        setupManager();
        when(userRepository.findById(8L)).thenReturn(Optional.of(activeUser(8L)));
        strictAssigneeGroups(8L, List.of(), List.of());

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.read(50L, null, 8L, null, null, null, false, false));

        verify(taskRepository, never()).findBoardTasksForManager(anyLong(), any(), any(), any(), any(), any(),
                anyBoolean(), anyBoolean());
        verify(groupMemberRepository, never()).findActiveGroupIdsByProjectIdAndUserId(anyLong(), anyLong());
    }

    @Test
    void managerMissingInactiveAndDeletedAssigneesAreNotFoundBeforeBoardQuery() {
        setupManager();
        when(userRepository.findById(8L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.read(50L, null, 8L, null, null, null, false, false));

        User inactive = activeUser(9L);
        inactive.setActive(false);
        when(userRepository.findById(9L)).thenReturn(Optional.of(inactive));
        assertThrows(ResourceNotFoundException.class,
                () -> service.read(50L, null, 9L, null, null, null, false, false));

        User deleted = activeUser(10L);
        deleted.setDeleted(true);
        when(userRepository.findById(10L)).thenReturn(Optional.of(deleted));
        assertThrows(ResourceNotFoundException.class,
                () -> service.read(50L, null, 10L, null, null, null, false, false));

        verify(taskRepository, never()).findBoardTasksForManager(anyLong(), any(), any(), any(), any(), any(),
                anyBoolean(), anyBoolean());
    }

    @Test
    void leaderCanFilterAssigneeInLedGroupButNotOutsideLedGroups() {
        setupStudent(List.of(100L), List.of(200L));
        when(userRepository.findById(8L)).thenReturn(Optional.of(activeUser(8L)));
        strictAssigneeGroups(8L, List.of(), List.of(100L));
        when(taskRepository.findBoardTasksForStudent(50L, List.of(100L), List.of(200L), 7L,
                null, 8L, null, null, null, false, false)).thenReturn(List.of());

        service.read(50L, null, 8L, null, null, null, false, false);

        when(userRepository.findById(9L)).thenReturn(Optional.of(activeUser(9L)));
        strictAssigneeGroups(9L, List.of(), List.of(200L));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.read(50L, null, 9L, null, null, null, false, false));
        verify(taskRepository, never()).findBoardTasksForStudent(50L, List.of(100L), List.of(200L), 7L,
                null, 9L, null, null, null, false, false);
    }

    @Test
    void memberCanFilterOnlySelfAndCannotWidenMemberScope() {
        setupStudent(List.of(), List.of(200L));
        when(userRepository.findById(7L)).thenReturn(Optional.of(activeUser(7L)));
        strictAssigneeGroups(7L, List.of(), List.of(200L));
        when(taskRepository.findBoardTasksForStudent(50L, List.of(-1L), List.of(200L), 7L,
                null, 7L, null, null, null, false, false)).thenReturn(List.of());

        service.read(50L, null, 7L, null, null, null, false, false);

        when(userRepository.findById(8L)).thenReturn(Optional.of(activeUser(8L)));
        strictAssigneeGroups(8L, List.of(), List.of(200L));
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.read(50L, null, 8L, null, null, null, false, false));
        verify(taskRepository, never()).findBoardTasksForStudent(50L, List.of(-1L), List.of(200L), 7L,
                null, 8L, null, null, null, false, false);
    }

    @Test
    void memberSelfFilterRequiresStrictActiveProjectScope() {
        setupStudent(List.of(), List.of(200L));
        when(userRepository.findById(7L)).thenReturn(Optional.of(activeUser(7L)));
        strictAssigneeGroups(7L, List.of(), List.of());

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.read(50L, null, 7L, null, null, null, false, false));
        verify(taskRepository, never()).findBoardTasksForStudent(anyLong(), anyList(), anyList(), anyLong(),
                any(), any(), any(), any(), any(), anyBoolean(), anyBoolean());
    }

    @Test
    void duplicateRepositoryRowsAreMappedOnceAndQueryOrderIsPreserved() {
        setupStudent(List.of(100L), List.of());
        TaskEntity first = task(1L, TaskStatus.TODO);
        TaskEntity second = task(2L, TaskStatus.TODO);
        when(taskRepository.findBoardTasksForStudent(anyLong(), anyList(), anyList(), anyLong(),
                isNull(), isNull(), isNull(), isNull(), isNull(), anyBoolean(), anyBoolean()))
                .thenReturn(List.of(first, first, second));

        ProjectTaskBoardResponse response = service.read(50L, null, null, null, null, null, false, false);

        assertEquals(List.of(1L, 2L), response.getColumns().get(0).getTasks().stream().map(t -> t.getId()).toList());
    }

    @Test
    void unrelatedUserIsDeniedBeforeAnyTaskQuery() {
        authenticate("student");
        User user = activeUser(7L);
        ProjectEntity project = project(50L, 1L);
        when(userRepository.findByUsername("student")).thenReturn(Optional.of(user));
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(50L)).thenReturn(Optional.of(project));
        when(permissionHelper.canViewProjectBoard(7L, project)).thenReturn(false);

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.read(50L, null, null, null, null, null, false, false));
        verify(taskRepository, never()).findBoardTasksForStudent(anyLong(), anyList(), anyList(), anyLong(),
                any(), any(), any(), any(), any(), anyBoolean(), anyBoolean());
    }

    private void authenticate(String name) {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn(name);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private User activeUser(Long id) {
        User user = new User();
        user.setId(id);
        user.setActive(true);
        user.setDeleted(false);
        return user;
    }

    private ProjectEntity setupManager() {
        authenticate("manager");
        User manager = activeUser(2L);
        manager.addRole(new Role("LAB_MANAGER", "manager"));
        ProjectEntity project = project(50L, 1L);
        when(userRepository.findByUsername("manager")).thenReturn(Optional.of(manager));
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(50L)).thenReturn(Optional.of(project));
        when(permissionHelper.canViewProjectBoard(2L, project)).thenReturn(true);
        return project;
    }

    private void strictAssigneeGroups(Long assigneeId, List<Long> leaderGroups, List<Long> memberGroups) {
        when(groupMemberRepository.findActiveGroupIdsByProjectIdAndUserIdAndRole(50L, assigneeId, GroupRole.LEADER))
                .thenReturn(leaderGroups);
        when(groupMemberRepository.findActiveGroupIdsByProjectIdAndUserIdAndRole(50L, assigneeId, GroupRole.MEMBER))
                .thenReturn(memberGroups);
    }

    private ProjectEntity setupStudent(List<Long> leaderGroups, List<Long> memberGroups) {
        authenticate("student");
        User user = activeUser(7L);
        ProjectEntity project = project(50L, 1L);
        when(userRepository.findByUsername("student")).thenReturn(Optional.of(user));
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(50L)).thenReturn(Optional.of(project));
        when(permissionHelper.canViewProjectBoard(7L, project)).thenReturn(true);
        when(groupMemberRepository.findActiveGroupIdsByProjectIdAndUserIdAndRole(50L, 7L, GroupRole.LEADER))
                .thenReturn(leaderGroups);
        when(groupMemberRepository.findActiveGroupIdsByProjectIdAndUserIdAndRole(50L, 7L, GroupRole.MEMBER))
                .thenReturn(memberGroups);
        return project;
    }

    private ProjectEntity project(Long id, Long labId) {
        Laboratory lab = new Laboratory(); lab.setId(labId);
        ProjectEntity project = ProjectEntity.builder().title("P").lab(lab).build(); project.setId(id);
        return project;
    }

    private GroupEntity group(Long id, ProjectEntity project, Long labId) {
        Laboratory lab = new Laboratory(); lab.setId(labId);
        GroupEntity group = GroupEntity.builder().name("G").project(project).lab(lab).build(); group.setId(id);
        return group;
    }

    private TaskEntity task(Long id, TaskStatus status) {
        TaskEntity task = TaskEntity.builder().title("Task " + id).milestoneId(1L).projectId(50L)
                .groupId(100L).status(status).build();
        task.setId(id);
        return task;
    }

    private List<TaskStatus> statuses(ProjectTaskBoardResponse response) {
        return response.getColumns().stream().map(column -> column.getStatus()).toList();
    }
}
