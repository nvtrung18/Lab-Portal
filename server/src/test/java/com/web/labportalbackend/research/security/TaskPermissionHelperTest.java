package com.web.labportalbackend.research.security;

import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.GroupMemberEntity;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.entity.TaskEntity;
import com.web.labportalbackend.research.enums.GroupRole;
import com.web.labportalbackend.research.enums.TaskStatus;
import com.web.labportalbackend.research.repository.GroupMemberRepository;
import com.web.labportalbackend.research.repository.GroupRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.repository.ReportRepository;
import com.web.labportalbackend.research.repository.TaskRepository;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.repository.Lock;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class TaskPermissionHelperTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private LaboratoryRepository laboratoryRepository;

    @InjectMocks
    private TaskPermissionHelper helper;

    @Test
    void managerCanViewBoardOnlyForManagedLabProject() {
        Laboratory managedLab = lab(1L);
        User manager = user(2L, "LAB_MANAGER");
        ProjectEntity ownProject = ProjectEntity.builder().lab(managedLab).build();
        ownProject.setId(50L);
        ProjectEntity otherProject = ProjectEntity.builder().lab(lab(2L)).build();
        otherProject.setId(60L);

        when(userRepository.findById(2L)).thenReturn(Optional.of(manager));
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(2L)).thenReturn(Optional.of(managedLab));

        assertTrue(helper.canViewProjectBoard(2L, ownProject));
        assertFalse(helper.canViewProjectBoard(2L, otherProject));
    }

    @Test
    void managerCanCreateOfficialTaskOnlyInExactActiveManagedLab() {
        User manager = user(2L, "LAB_MANAGER");
        Laboratory lab = lab(1L);
        ProjectEntity project = ProjectEntity.builder().lab(lab).build();
        project.setId(50L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(manager));
        when(laboratoryRepository.existsByIdAndManagerIdAndActiveTrueAndDeletedFalse(1L, 2L)).thenReturn(true);

        assertTrue(helper.canCreateOfficialTask(2L, project));

        verify(laboratoryRepository).existsByIdAndManagerIdAndActiveTrueAndDeletedFalse(1L, 2L);
        verify(laboratoryRepository, never()).findFirstByManagerIdAndDeletedFalse(2L);
    }

    @Test
    void officialTaskCreateFailsClosedForOtherLabInactiveOrDeletedActorsAndResources() {
        User manager = user(2L, "LAB_MANAGER");
        Laboratory lab = lab(1L);
        ProjectEntity project = ProjectEntity.builder().lab(lab).build();
        project.setId(50L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(manager));
        when(laboratoryRepository.existsByIdAndManagerIdAndActiveTrueAndDeletedFalse(1L, 2L)).thenReturn(false);

        assertFalse(helper.canCreateOfficialTask(2L, project));

        manager.setActive(false);
        assertFalse(helper.canCreateOfficialTask(2L, project));
        manager.setActive(true);
        manager.setDeleted(true);
        assertFalse(helper.canCreateOfficialTask(2L, project));
        manager.setDeleted(false);
        project.setActive(false);
        assertFalse(helper.canCreateOfficialTask(2L, project));
        project.setActive(true);
        project.setDeleted(true);
        assertFalse(helper.canCreateOfficialTask(2L, project));
        project.setDeleted(false);
        lab.setActive(false);
        assertFalse(helper.canCreateOfficialTask(2L, project));
        lab.setActive(true);
        lab.setDeleted(true);
        assertFalse(helper.canCreateOfficialTask(2L, project));
    }

    @Test
    void officialTaskCreateDoesNotDependOnWhichManagedLabWouldBeReturnedFirst() {
        User manager = user(2L, "LAB_MANAGER");
        Laboratory targetLab = lab(2L);
        ProjectEntity project = ProjectEntity.builder().lab(targetLab).build();
        project.setId(50L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(manager));
        when(laboratoryRepository.existsByIdAndManagerIdAndActiveTrueAndDeletedFalse(2L, 2L)).thenReturn(true);

        assertTrue(helper.canCreateOfficialTask(2L, project));

        verify(laboratoryRepository, never()).findFirstByManagerIdAndDeletedFalse(2L);
    }

    @Test
    void studentLeaderAndMemberSystemActorsCannotCreateOfficialTasks() {
        Laboratory lab = lab(1L);
        ProjectEntity project = ProjectEntity.builder().lab(lab).build();
        project.setId(50L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, "STUDENT")));
        when(userRepository.findById(8L)).thenReturn(Optional.of(user(8L, "LEADER")));
        when(userRepository.findById(9L)).thenReturn(Optional.of(user(9L, "MEMBER")));

        assertFalse(helper.canCreateOfficialTask(7L, project));
        assertFalse(helper.canCreateOfficialTask(8L, project));
        assertFalse(helper.canCreateOfficialTask(9L, project));

        verify(laboratoryRepository, never()).existsByIdAndManagerIdAndActiveTrueAndDeletedFalse(any(), any());
    }

    @Test
    void activeStudentMembershipCanViewProjectBoard() {
        User student = user(7L, "STUDENT");
        ProjectEntity project = ProjectEntity.builder().lab(lab(1L)).build();
        project.setId(50L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(student));
        when(groupMemberRepository.findActiveGroupIdsByProjectIdAndUserIdAndRole(50L, 7L, GroupRole.LEADER))
                .thenReturn(List.of(100L));

        assertTrue(helper.canViewProjectBoard(7L, project));
    }

    @Test
    void projectBoardFailsClosedForInactiveUserOrMissingOwnership() {
        User inactive = user(7L, "STUDENT");
        inactive.setActive(false);
        when(userRepository.findById(7L)).thenReturn(Optional.of(inactive));

        assertFalse(helper.canViewProjectBoard(7L, ProjectEntity.builder().build()));
    }

    @Test
    void projectBoardFailsClosedForInactiveLabOrInconsistentProjectGroup() {
        User student = user(7L, "STUDENT");
        Laboratory projectLab = lab(1L);
        Laboratory otherLab = lab(2L);
        ProjectEntity project = ProjectEntity.builder().lab(projectLab).group(group(otherLab)).build();
        project.setId(50L);
        project.getGroup().setId(100L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(student));

        assertFalse(helper.canViewProjectBoard(7L, project));

        project.setGroup(null);
        projectLab.setActive(false);
        assertFalse(helper.canViewProjectBoard(7L, project));
        verify(groupMemberRepository, never())
                .findActiveGroupIdsByProjectIdAndUserIdAndRole(50L, 7L, GroupRole.LEADER);
    }

    @Test
    void assigneeCanUpdateOwnTaskStatusWhenTaskHasGroupScope() {
        TaskEntity task = task();
        task.setAssigneeId(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, "STUDENT")));

        assertTrue(helper.canUpdateTaskStatus(7L, task));
    }

    @Test
    void normalMemberCanViewButCannotManageTaskMetadata() {
        TaskEntity task = task();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, "STUDENT")));
        when(groupMemberRepository.findActiveRoleByGroupIdAndUserId(100L, 7L))
                .thenReturn(Optional.of(GroupRole.MEMBER));

        assertTrue(helper.canViewTask(7L, task));
        assertFalse(helper.canManageTask(7L, task));
    }

    @Test
    void leaderOfTaskGroupCanManageAndUpdateTask() {
        TaskEntity task = task();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, "STUDENT")));
        when(groupMemberRepository.findActiveRoleByGroupIdAndUserId(100L, 7L))
                .thenReturn(Optional.of(GroupRole.LEADER));

        assertTrue(helper.canManageTask(7L, task));
        assertTrue(helper.canUpdateTaskStatus(7L, task));
    }

    @Test
    void labManagerOfTaskProjectCanManageTask() {
        TaskEntity task = task();
        Laboratory lab = lab(1L);
        ProjectEntity project = ProjectEntity.builder().lab(lab).build();
        User manager = user(2L, "LAB_MANAGER");

        when(userRepository.findById(2L)).thenReturn(Optional.of(manager));
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(50L)).thenReturn(Optional.of(project));
        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(100L)).thenReturn(Optional.of(group(lab)));
        when(laboratoryRepository.existsByIdAndManagerIdAndActiveTrueAndDeletedFalse(1L, 2L)).thenReturn(true);

        assertTrue(helper.canManageTask(2L, task));
        assertTrue(helper.canUpdateTaskStatus(2L, task));
    }

    @Test
    void unrelatedUserCannotViewOrUpdateTask() {
        TaskEntity task = task();
        when(userRepository.findById(9L)).thenReturn(Optional.of(user(9L, "STUDENT")));
        when(groupMemberRepository.findActiveRoleByGroupIdAndUserId(100L, 9L)).thenReturn(Optional.empty());

        assertFalse(helper.canViewTask(9L, task));
        assertFalse(helper.canUpdateTaskStatus(9L, task));
    }

    @Test
    void taskWithMissingOwnershipFailsClosed() {
        TaskEntity task = task();
        task.setProjectId(null);
        task.setGroupId(null);
        task.setAssigneeId(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, "STUDENT")));

        assertFalse(helper.canViewTask(7L, task));
        assertFalse(helper.canManageTask(7L, task));
        assertFalse(helper.canUpdateTaskStatus(7L, task));
        verify(groupMemberRepository, never()).findActiveRoleByGroupIdAndUserId(100L, 7L);
    }

    @Test
    void mismatchedProjectAndGroupLabFailsClosedForManager() {
        TaskEntity task = task();
        Laboratory managedLab = lab(1L);
        Laboratory otherLab = lab(2L);
        User manager = user(2L, "LAB_MANAGER");

        when(userRepository.findById(2L)).thenReturn(Optional.of(manager));
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(50L))
                .thenReturn(Optional.of(ProjectEntity.builder().lab(managedLab).build()));
        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(100L)).thenReturn(Optional.of(group(otherLab)));

        assertFalse(helper.isManagerOfTaskProjectOrLab(2L, task));
    }

    @Test
    void managerResolutionFailsClosedWhenNonNullProjectCannotResolve() {
        TaskEntity task = task();
        User manager = user(2L, "LAB_MANAGER");
        when(userRepository.findById(2L)).thenReturn(Optional.of(manager));
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(50L)).thenReturn(Optional.empty());

        assertFalse(helper.isManagerOfTaskProjectOrLab(2L, task));
        verify(laboratoryRepository, never())
                .existsByIdAndManagerIdAndActiveTrueAndDeletedFalse(any(), any());
    }

    @Test
    void managerResolutionFailsClosedWhenNonNullGroupCannotResolve() {
        TaskEntity task = task();
        User manager = user(2L, "LAB_MANAGER");
        when(userRepository.findById(2L)).thenReturn(Optional.of(manager));
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(50L))
                .thenReturn(Optional.of(ProjectEntity.builder().lab(lab(1L)).build()));
        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(100L)).thenReturn(Optional.empty());

        assertFalse(helper.isManagerOfTaskProjectOrLab(2L, task));
        verify(laboratoryRepository, never())
                .existsByIdAndManagerIdAndActiveTrueAndDeletedFalse(any(), any());
    }

    @Test
    void managerResolutionFailsClosedWhenResolvedScopeHasNullLab() {
        TaskEntity task = task();
        User manager = user(2L, "LAB_MANAGER");
        when(userRepository.findById(2L)).thenReturn(Optional.of(manager));
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(50L))
                .thenReturn(Optional.of(ProjectEntity.builder().lab(null).build()));

        assertFalse(helper.isManagerOfTaskProjectOrLab(2L, task));
    }

    @Test
    void managerResolutionAllowsFallbackOnlyForGenuinelyNullProjectId() {
        TaskEntity task = task();
        task.setProjectId(null);
        User manager = user(2L, "LAB_MANAGER");
        when(userRepository.findById(2L)).thenReturn(Optional.of(manager));
        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(100L))
                .thenReturn(Optional.of(group(lab(1L))));
        when(laboratoryRepository.existsByIdAndManagerIdAndActiveTrueAndDeletedFalse(1L, 2L))
                .thenReturn(true);

        assertTrue(helper.isManagerOfTaskProjectOrLab(2L, task));
    }

    @Test
    void lockedStatusScopeUsesProjectThenGroupThenExactLaboratoryReads() {
        TaskEntity task = task();
        Laboratory laboratory = lab(1L);
        when(projectRepository.findByIdForStatusAuthorization(50L))
                .thenReturn(Optional.of(ProjectEntity.builder().lab(laboratory).build()));
        when(groupRepository.findByIdForStatusAuthorization(100L))
                .thenReturn(Optional.of(group(laboratory)));
        when(laboratoryRepository.findByIdForStatusAuthorization(1L))
                .thenReturn(Optional.of(laboratory));

        TaskPermissionHelper.StatusAuthorizationScope scope =
                helper.resolveStatusAuthorizationScope(task);

        assertTrue(scope.valid());
        assertTrue(Long.valueOf(1L).equals(scope.laboratoryId()));
        var order = inOrder(projectRepository, groupRepository, laboratoryRepository);
        order.verify(projectRepository).findByIdForStatusAuthorization(50L);
        order.verify(groupRepository).findByIdForStatusAuthorization(100L);
        order.verify(laboratoryRepository).findByIdForStatusAuthorization(1L);
        verify(projectRepository, never()).findByIdAndDeletedFalseAndActiveTrue(any());
        verify(groupRepository, never()).findByIdAndDeletedFalseAndActiveTrue(any());
    }

    @Test
    void lockedStatusScopeFailsClosedWhenEitherNonNullScopeCannotResolve() {
        TaskEntity task = task();
        when(projectRepository.findByIdForStatusAuthorization(50L)).thenReturn(Optional.empty());
        when(groupRepository.findByIdForStatusAuthorization(100L))
                .thenReturn(Optional.of(group(lab(1L))));

        TaskPermissionHelper.StatusAuthorizationScope missingProject =
                helper.resolveStatusAuthorizationScope(task);

        assertFalse(missingProject.valid());
        verify(groupRepository).findByIdForStatusAuthorization(100L);
        verify(laboratoryRepository, never()).findByIdForStatusAuthorization(any());
    }

    @Test
    void lockedStatusScopeFailsClosedWhenResolvedLabsDisagree() {
        TaskEntity task = task();
        when(projectRepository.findByIdForStatusAuthorization(50L))
                .thenReturn(Optional.of(ProjectEntity.builder().lab(lab(1L)).build()));
        when(groupRepository.findByIdForStatusAuthorization(100L))
                .thenReturn(Optional.of(group(lab(2L))));

        TaskPermissionHelper.StatusAuthorizationScope scope =
                helper.resolveStatusAuthorizationScope(task);

        assertFalse(scope.valid());
        verify(laboratoryRepository, never()).findByIdForStatusAuthorization(any());
    }

    @Test
    void lockedStatusScopeFailsClosedWhenEitherResolvedScopeHasNoLab() {
        TaskEntity task = task();
        when(projectRepository.findByIdForStatusAuthorization(50L))
                .thenReturn(Optional.of(ProjectEntity.builder().lab(lab(1L)).build()));
        when(groupRepository.findByIdForStatusAuthorization(100L))
                .thenReturn(Optional.of(group(null)));

        TaskPermissionHelper.StatusAuthorizationScope scope =
                helper.resolveStatusAuthorizationScope(task);

        assertFalse(scope.valid());
        verify(laboratoryRepository, never()).findByIdForStatusAuthorization(any());
    }

    @Test
    void lockedStatusScopeFallsBackOnlyWhenProjectIdIsGenuinelyNull() {
        TaskEntity task = task();
        task.setProjectId(null);
        Laboratory laboratory = lab(1L);
        when(groupRepository.findByIdForStatusAuthorization(100L))
                .thenReturn(Optional.of(group(laboratory)));
        when(laboratoryRepository.findByIdForStatusAuthorization(1L))
                .thenReturn(Optional.of(laboratory));

        TaskPermissionHelper.StatusAuthorizationScope scope =
                helper.resolveStatusAuthorizationScope(task);

        assertTrue(scope.valid());
        assertTrue(Long.valueOf(1L).equals(scope.laboratoryId()));
        verify(projectRepository, never()).findByIdForStatusAuthorization(any());
        verify(groupRepository).findByIdForStatusAuthorization(100L);
        verify(laboratoryRepository).findByIdForStatusAuthorization(1L);
    }

    @Test
    void finalAuthorizationRepositoriesDeclareTaskFirstCurrentLockingReads() throws Exception {
        assertLock(TaskRepository.class, "findByIdForUpdate",
                LockModeType.PESSIMISTIC_WRITE, Long.class);
        assertLock(UserRepository.class, "findByIdForStatusAuthorization",
                LockModeType.PESSIMISTIC_READ, Long.class);
        assertLock(ProjectRepository.class, "findByIdForStatusAuthorization",
                LockModeType.PESSIMISTIC_READ, Long.class);
        assertLock(GroupRepository.class, "findByIdForStatusAuthorization",
                LockModeType.PESSIMISTIC_READ, Long.class);
        assertLock(LaboratoryRepository.class, "findByIdForStatusAuthorization",
                LockModeType.PESSIMISTIC_READ, Long.class);
        assertLock(LaboratoryRepository.class, "findManagedByIdForStatusAuthorization",
                LockModeType.PESSIMISTIC_READ, Long.class, Long.class);
        assertLock(GroupMemberRepository.class, "findActiveForStatusAuthorization",
                LockModeType.PESSIMISTIC_READ, Long.class, Long.class);
        assertLock(ReportRepository.class, "findLatestApprovedForStatusAuthorization",
                LockModeType.PESSIMISTIC_READ, Long.class);
    }

    @Test
    void lockedStatusMembershipUsesOnlyCurrentLockingRead() {
        TaskEntity task = task();
        GroupMemberEntity membership = GroupMemberEntity.builder()
                .role(GroupRole.LEADER)
                .build();
        when(groupMemberRepository.findActiveForStatusAuthorization(100L, 7L))
                .thenReturn(Optional.of(membership));

        assertTrue(helper.findGroupRoleForStatusAuthorization(7L, task) == GroupRole.LEADER);

        verify(groupMemberRepository).findActiveForStatusAuthorization(100L, 7L);
        verify(groupMemberRepository, never()).findActiveRoleByGroupIdAndUserId(any(), any());
    }

    @Test
    void cancelledStatusIsNotMappedToBlockedForScopeChecks() {
        TaskEntity cancelled = task();
        cancelled.setStatus(TaskStatus.CANCELLED);
        TaskEntity blocked = task();
        blocked.setStatus(TaskStatus.BLOCKED);
        cancelled.setAssigneeId(7L);
        blocked.setAssigneeId(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(7L, "STUDENT")));

        assertTrue(helper.canUpdateTaskStatus(7L, cancelled));
        assertTrue(helper.canUpdateTaskStatus(7L, blocked));
        assertFalse(cancelled.getStatus() == blocked.getStatus());
    }

    private void assertLock(
            Class<?> repositoryType,
            String methodName,
            LockModeType expected,
            Class<?>... parameterTypes
    ) throws Exception {
        Method method = repositoryType.getMethod(methodName, parameterTypes);
        Lock lock = method.getAnnotation(Lock.class);
        assertNotNull(lock, repositoryType.getSimpleName() + "." + methodName);
        assertEquals(expected, lock.value(), repositoryType.getSimpleName() + "." + methodName);
    }

    private TaskEntity task() {
        return TaskEntity.builder()
                .projectId(50L)
                .groupId(100L)
                .milestoneId(10L)
                .title("Prepare dataset")
                .status(TaskStatus.TODO)
                .build();
    }

    private User user(Long id, String roleName) {
        User user = new User();
        user.setId(id);
        user.addRole(new Role(roleName, roleName));
        return user;
    }

    private Laboratory lab(Long id) {
        Laboratory lab = new Laboratory();
        lab.setId(id);
        return lab;
    }

    private GroupEntity group(Laboratory lab) {
        return GroupEntity.builder()
                .lab(lab)
                .build();
    }
}
