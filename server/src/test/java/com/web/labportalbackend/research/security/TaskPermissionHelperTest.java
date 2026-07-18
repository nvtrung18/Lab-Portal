package com.web.labportalbackend.research.security;

import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.entity.TaskEntity;
import com.web.labportalbackend.research.enums.GroupRole;
import com.web.labportalbackend.research.enums.TaskStatus;
import com.web.labportalbackend.research.repository.GroupMemberRepository;
import com.web.labportalbackend.research.repository.GroupRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(2L)).thenReturn(Optional.of(lab));
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(50L)).thenReturn(Optional.of(project));
        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(100L)).thenReturn(Optional.of(group(lab)));

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
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(2L)).thenReturn(Optional.of(managedLab));
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(50L))
                .thenReturn(Optional.of(ProjectEntity.builder().lab(managedLab).build()));
        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(100L)).thenReturn(Optional.of(group(otherLab)));

        assertFalse(helper.isManagerOfTaskProjectOrLab(2L, task));
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
