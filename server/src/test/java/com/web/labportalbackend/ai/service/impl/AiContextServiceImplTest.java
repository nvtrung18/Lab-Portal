package com.web.labportalbackend.ai.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.web.labportalbackend.ai.service.AiContextService;
import com.web.labportalbackend.ai.service.AiResearchContext;
import com.web.labportalbackend.ai.service.AiResearchContextRequest;
import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.MilestoneEntity;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.entity.TaskEntity;
import com.web.labportalbackend.research.enums.GroupRole;
import com.web.labportalbackend.research.enums.MilestoneStatus;
import com.web.labportalbackend.research.enums.ProjectStatus;
import com.web.labportalbackend.research.enums.TaskStatus;
import com.web.labportalbackend.research.repository.GroupMemberRepository;
import com.web.labportalbackend.research.repository.GroupRepository;
import com.web.labportalbackend.research.repository.MilestoneRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.repository.TaskRepository;
import com.web.labportalbackend.research.security.TaskPermissionHelper;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AiContextServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private GroupRepository groupRepository;
    @Mock private GroupMemberRepository groupMemberRepository;
    @Mock private MilestoneRepository milestoneRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private TaskPermissionHelper permissionHelper;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void managerBuildsMinimalContextForExactlyManagedSecondLab() {
        authenticate("manager");
        User manager = user(7L, "LAB_MANAGER", "ROLE_STUDENT");
        ProjectEntity project = project(50L, 2L);
        GroupEntity group = group(100L, project, 2L);
        MilestoneEntity milestone = milestone(10L, project, group);
        TaskEntity task = task(20L, 50L, 100L, 10L, 7L);
        when(userRepository.findByUsername("manager")).thenReturn(Optional.of(manager));
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(50L)).thenReturn(Optional.of(project));
        when(permissionHelper.canViewProjectContext(7L, project)).thenReturn(true);
        when(groupRepository.findByProjectIdAndDeletedFalseAndActiveTrue(50L)).thenReturn(List.of(group));
        when(milestoneRepository.findByProjectIdAndDeletedFalseAndActiveTrueOrderByDeadlineAscCreatedAtAsc(50L))
                .thenReturn(List.of(milestone));
        when(taskRepository.findBoardTasksForManager(50L, null, null, null, null, null, true, true))
                .thenReturn(List.of(task));
        when(permissionHelper.canViewTask(7L, task)).thenReturn(true);

        AiContextService service = service();
        AiResearchContext context = service.buildResearchContext(new AiResearchContextRequest(50L, null));

        assertEquals(7L, context.identity().userId());
        assertEquals(List.of("LAB_MANAGER", "STUDENT"), context.identity().roles());
        assertEquals(2L, context.laboratory().id());
        assertEquals("Lab 2", context.laboratory().name());
        assertEquals(List.of(100L), context.groups().stream().map(AiResearchContext.Group::id).toList());
        assertEquals(null, context.groups().getFirst().role());
        assertEquals(List.of(10L), context.milestones().stream().map(AiResearchContext.Milestone::id).toList());
        assertEquals(List.of(20L), context.tasks().stream().map(AiResearchContext.Task::id).toList());
        verify(permissionHelper).canViewProjectContext(7L, project);
        verify(permissionHelper).canViewTask(7L, task);
    }

    @Test
    void managerExcludesMilestonesWhoseGroupsAreInactiveCrossLabOrCrossProject() {
        authenticate("manager");
        User manager = user(7L, "LAB_MANAGER");
        ProjectEntity project = project(50L, 2L);
        GroupEntity coherentGroup = group(100L, project, 2L);
        GroupEntity inactiveGroup = group(101L, project, 2L);
        inactiveGroup.setActive(false);
        GroupEntity crossLabGroup = group(102L, project, 3L);
        GroupEntity crossProjectGroup = group(103L, project(51L, 2L), 2L);
        MilestoneEntity coherentMilestone = milestone(10L, project, coherentGroup);
        MilestoneEntity inactiveGroupMilestone = milestone(11L, project, inactiveGroup);
        MilestoneEntity crossLabMilestone = milestone(12L, project, crossLabGroup);
        MilestoneEntity crossProjectMilestone = milestone(13L, project, crossProjectGroup);
        when(userRepository.findByUsername("manager")).thenReturn(Optional.of(manager));
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(50L)).thenReturn(Optional.of(project));
        when(permissionHelper.canViewProjectContext(7L, project)).thenReturn(true);
        when(groupRepository.findByProjectIdAndDeletedFalseAndActiveTrue(50L)).thenReturn(List.of(coherentGroup));
        when(milestoneRepository.findByProjectIdAndDeletedFalseAndActiveTrueOrderByDeadlineAscCreatedAtAsc(50L))
                .thenReturn(List.of(coherentMilestone, inactiveGroupMilestone, crossLabMilestone,
                        crossProjectMilestone));
        when(taskRepository.findBoardTasksForManager(50L, null, null, null, null, null, true, true))
                .thenReturn(List.of());

        AiResearchContext context = service().buildResearchContext(new AiResearchContextRequest(50L, null));

        assertEquals(List.of(10L), context.milestones().stream().map(AiResearchContext.Milestone::id).toList());
    }

    @Test
    void crossLabProjectIsDeniedBeforeContextQueries() {
        authenticate("manager");
        User manager = user(7L, "LAB_MANAGER");
        ProjectEntity project = project(50L, 3L);
        when(userRepository.findByUsername("manager")).thenReturn(Optional.of(manager));
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(50L)).thenReturn(Optional.of(project));
        when(permissionHelper.canViewProjectContext(7L, project)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> service().buildResearchContext(new AiResearchContextRequest(50L, null)));

        verifyNoInteractions(groupRepository, groupMemberRepository, milestoneRepository, taskRepository);
    }

    @Test
    void studentCrossLabProjectIsDeniedBeforeContextQueries() {
        authenticate("student");
        User student = user(8L, "STUDENT");
        ProjectEntity project = project(50L, 3L);
        when(userRepository.findByUsername("student")).thenReturn(Optional.of(student));
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(50L)).thenReturn(Optional.of(project));
        when(permissionHelper.canViewProjectContext(8L, project)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> service().buildResearchContext(new AiResearchContextRequest(50L, null)));

        verifyNoInteractions(groupRepository, groupMemberRepository, milestoneRepository, taskRepository);
    }

    @Test
    void studentReturnsOnlyOwnViewableTasksAndTheirMilestones() {
        authenticate("student");
        User student = user(8L, "STUDENT");
        ProjectEntity project = project(50L, 2L);
        GroupEntity memberGroup = group(100L, project, 2L);
        MilestoneEntity assigned = milestone(10L, project, memberGroup);
        assigned.setAssignedToStudent(student);
        TaskEntity ownTask = task(20L, 50L, 100L, 10L, 8L);
        TaskEntity otherTask = task(21L, 50L, 100L, 10L, 9L);
        when(userRepository.findByUsername("student")).thenReturn(Optional.of(student));
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(50L)).thenReturn(Optional.of(project));
        when(permissionHelper.canViewProjectContext(8L, project)).thenReturn(true);
        when(groupMemberRepository.findActiveGroupIdsByProjectIdAndUserIdAndRole(50L, 8L, GroupRole.LEADER))
                .thenReturn(List.of());
        when(groupMemberRepository.findActiveGroupIdsByProjectIdAndUserIdAndRole(50L, 8L, GroupRole.MEMBER))
                .thenReturn(List.of(100L));
        when(groupRepository.findByProjectIdAndDeletedFalseAndActiveTrue(50L)).thenReturn(List.of(memberGroup));
        when(milestoneRepository.findByProjectIdAndDeletedFalseAndActiveTrueOrderByDeadlineAscCreatedAtAsc(50L))
                .thenReturn(List.of(assigned));
        when(taskRepository.findBoardTasksForStudent(50L, List.of(-1L), List.of(100L), 8L,
                null, null, null, null, null, true, true)).thenReturn(List.of(ownTask, otherTask));
        when(permissionHelper.canViewTask(8L, ownTask)).thenReturn(true);
        when(permissionHelper.canViewTask(8L, otherTask)).thenReturn(false);

        AiResearchContext context = service().buildResearchContext(new AiResearchContextRequest(50L, null));

        assertEquals(List.of(100L), context.groups().stream().map(AiResearchContext.Group::id).toList());
        assertEquals(GroupRole.MEMBER, context.groups().getFirst().role());
        assertEquals(List.of(10L), context.milestones().stream().map(AiResearchContext.Milestone::id).toList());
        assertEquals(List.of(20L), context.tasks().stream().map(AiResearchContext.Task::id).toList());
    }

    @Test
    void missingExplicitGroupFailsRatherThanReturningPartialContext() {
        authenticate("student");
        User student = user(8L, "STUDENT");
        ProjectEntity project = project(50L, 2L);
        when(userRepository.findByUsername("student")).thenReturn(Optional.of(student));
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(50L)).thenReturn(Optional.of(project));
        when(permissionHelper.canViewProjectContext(8L, project)).thenReturn(true);
        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(100L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service().buildResearchContext(new AiResearchContextRequest(50L, 100L)));

        verifyNoInteractions(groupMemberRepository, milestoneRepository, taskRepository);
    }

    @Test
    void invalidRequestIdsAreRejectedBeforeRepositoryAccess() {
        assertThrows(IllegalArgumentException.class, () -> new AiResearchContextRequest(0L, null));
        assertThrows(IllegalArgumentException.class, () -> new AiResearchContextRequest(1L, 0L));
        verifyNoInteractions(userRepository, projectRepository, groupRepository, groupMemberRepository,
                milestoneRepository, taskRepository, permissionHelper);
    }

    @Test
    void leaderContextIncludesOnlyLedGroupMaterial() {
        authenticate("leader");
        User leader = user(8L, "STUDENT");
        ProjectEntity project = project(50L, 2L);
        GroupEntity ledGroup = group(100L, project, 2L);
        GroupEntity otherGroup = group(101L, project, 2L);
        MilestoneEntity ledMilestone = milestone(10L, project, ledGroup);
        MilestoneEntity otherMilestone = milestone(11L, project, otherGroup);
        TaskEntity ledTask = task(20L, 50L, 100L, 10L, 9L);
        when(userRepository.findByUsername("leader")).thenReturn(Optional.of(leader));
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(50L)).thenReturn(Optional.of(project));
        when(permissionHelper.canViewProjectContext(8L, project)).thenReturn(true);
        when(groupMemberRepository.findActiveGroupIdsByProjectIdAndUserIdAndRole(50L, 8L, GroupRole.LEADER))
                .thenReturn(List.of(100L));
        when(groupMemberRepository.findActiveGroupIdsByProjectIdAndUserIdAndRole(50L, 8L, GroupRole.MEMBER))
                .thenReturn(List.of());
        when(groupRepository.findByProjectIdAndDeletedFalseAndActiveTrue(50L))
                .thenReturn(List.of(ledGroup, otherGroup));
        when(milestoneRepository.findByProjectIdAndDeletedFalseAndActiveTrueOrderByDeadlineAscCreatedAtAsc(50L))
                .thenReturn(List.of(ledMilestone, otherMilestone));
        when(taskRepository.findBoardTasksForStudent(50L, List.of(100L), List.of(-1L), 8L,
                null, null, null, null, null, true, true)).thenReturn(List.of(ledTask));
        when(permissionHelper.canViewTask(8L, ledTask)).thenReturn(true);

        AiResearchContext context = service().buildResearchContext(new AiResearchContextRequest(50L, null));

        assertEquals(List.of(100L), context.groups().stream().map(AiResearchContext.Group::id).toList());
        assertEquals(GroupRole.LEADER, context.groups().getFirst().role());
        assertEquals(List.of(10L), context.milestones().stream().map(AiResearchContext.Milestone::id).toList());
        assertEquals(List.of(20L), context.tasks().stream().map(AiResearchContext.Task::id).toList());
    }

    @Test
    void explicitCrossGroupAnchorIsDeniedBeforeContextQueries() {
        authenticate("student");
        User student = user(8L, "STUDENT");
        ProjectEntity project = project(50L, 2L);
        ProjectEntity otherProject = project(51L, 2L);
        GroupEntity otherGroup = group(101L, otherProject, 2L);
        when(userRepository.findByUsername("student")).thenReturn(Optional.of(student));
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(50L)).thenReturn(Optional.of(project));
        when(permissionHelper.canViewProjectContext(8L, project)).thenReturn(true);
        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(101L)).thenReturn(Optional.of(otherGroup));

        assertThrows(AccessDeniedException.class,
                () -> service().buildResearchContext(new AiResearchContextRequest(50L, 101L)));

        verifyNoInteractions(groupMemberRepository, milestoneRepository, taskRepository);
    }

    @Test
    void missingProjectFailsBeforeAuthorizationOrChildQueries() {
        authenticate("student");
        User student = user(8L, "STUDENT");
        when(userRepository.findByUsername("student")).thenReturn(Optional.of(student));
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(50L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service().buildResearchContext(new AiResearchContextRequest(50L, null)));

        verifyNoInteractions(groupRepository, groupMemberRepository, milestoneRepository, taskRepository, permissionHelper);
    }

    @Test
    void authorizedProjectWithNoChildrenReturnsImmutableEmptyLists() {
        authenticate("manager");
        User manager = user(7L, "LAB_MANAGER");
        ProjectEntity project = project(50L, 2L);
        when(userRepository.findByUsername("manager")).thenReturn(Optional.of(manager));
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(50L)).thenReturn(Optional.of(project));
        when(permissionHelper.canViewProjectContext(7L, project)).thenReturn(true);
        when(groupRepository.findByProjectIdAndDeletedFalseAndActiveTrue(50L)).thenReturn(List.of());
        when(milestoneRepository.findByProjectIdAndDeletedFalseAndActiveTrueOrderByDeadlineAscCreatedAtAsc(50L))
                .thenReturn(List.of());
        when(taskRepository.findBoardTasksForManager(50L, null, null, null, null, null, true, true))
                .thenReturn(List.of());

        AiResearchContext context = service().buildResearchContext(new AiResearchContextRequest(50L, null));

        assertTrue(context.groups().isEmpty());
        assertTrue(context.milestones().isEmpty());
        assertTrue(context.tasks().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> context.tasks().add(null));
    }

    private AiContextService service() {
        return new AiContextServiceImpl(userRepository, projectRepository, groupRepository, groupMemberRepository,
                milestoneRepository, taskRepository, permissionHelper);
    }

    private void authenticate(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(username, "N/A", List.of()));
    }

    private User user(Long id, String... roles) {
        User user = new User();
        user.setId(id);
        for (String role : roles) {
            user.addRole(new Role(role, role));
        }
        return user;
    }

    private ProjectEntity project(Long id, Long labId) {
        Laboratory laboratory = new Laboratory();
        laboratory.setId(labId);
        laboratory.setLabName("Lab " + labId);
        ProjectEntity project = ProjectEntity.builder().lab(laboratory).code("P-" + id).title("Project " + id)
                .status(ProjectStatus.ONGOING).startDate(LocalDate.of(2026, 1, 1)).endDate(LocalDate.of(2026, 12, 31))
                .build();
        project.setId(id);
        return project;
    }

    private GroupEntity group(Long id, ProjectEntity project, Long labId) {
        Laboratory laboratory = new Laboratory();
        laboratory.setId(labId);
        GroupEntity group = GroupEntity.builder().lab(laboratory).project(project).name("Group " + id).build();
        group.setId(id);
        return group;
    }

    private MilestoneEntity milestone(Long id, ProjectEntity project, GroupEntity group) {
        MilestoneEntity milestone = MilestoneEntity.builder().project(project).group(group).title("Milestone " + id)
                .status(MilestoneStatus.NOT_STARTED).startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 2, 1)).deadline(LocalDate.of(2026, 2, 1)).progressPercent(0).build();
        milestone.setId(id);
        return milestone;
    }

    private TaskEntity task(Long id, Long projectId, Long groupId, Long milestoneId, Long assigneeId) {
        TaskEntity task = TaskEntity.builder().projectId(projectId).groupId(groupId).milestoneId(milestoneId)
                .assigneeId(assigneeId).title("Task " + id).status(TaskStatus.TODO).build();
        task.setId(id);
        return task;
    }
}
