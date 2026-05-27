package com.web.labportalbackend.research.service;

import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.exception.InvalidAssigneeException;
import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.dto.request.AssignTaskRequest;
import com.web.labportalbackend.research.dto.request.CreateTaskRequest;
import com.web.labportalbackend.research.dto.request.UpdateTaskStatusRequest;
import com.web.labportalbackend.research.dto.response.TaskResponse;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.MilestoneEntity;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.entity.TaskEntity;
import com.web.labportalbackend.research.enums.TaskStatus;
import com.web.labportalbackend.research.enums.GroupRole;
import com.web.labportalbackend.research.repository.GroupMemberRepository;
import com.web.labportalbackend.research.repository.GroupRepository;
import com.web.labportalbackend.research.repository.MilestoneRepository;
import com.web.labportalbackend.research.repository.ReportRepository;
import com.web.labportalbackend.research.repository.TaskRepository;
import com.web.labportalbackend.research.service.impl.TaskServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
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
class TaskServiceImplTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private MilestoneRepository milestoneRepository;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private LaboratoryRepository laboratoryRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TaskServiceImpl taskService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createTask_savesTodoTaskWhenMilestoneExists() {
        CreateTaskRequest request = new CreateTaskRequest();
        request.setMilestoneId(10L);
        request.setTitle("Prepare dataset");

        when(milestoneRepository.existsById(10L)).thenReturn(true);
        when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> {
            TaskEntity task = invocation.getArgument(0);
            task.setId(20L);
            return task;
        });

        TaskResponse response = taskService.createTask(request);

        assertEquals(20L, response.getId());
        assertEquals(10L, response.getMilestoneId());
        assertEquals("Prepare dataset", response.getTitle());
        assertEquals(TaskStatus.TODO, response.getStatus());
        assertNull(response.getAssignedToStudentId());

        ArgumentCaptor<TaskEntity> captor = ArgumentCaptor.forClass(TaskEntity.class);
        verify(taskRepository).save(captor.capture());
        assertEquals(TaskStatus.TODO, captor.getValue().getStatus());
    }

    @Test
    void assign_updatesAssigneeWhenUserBelongsToProjectGroup() {
        TaskEntity task = task(20L, 10L, null);
        MilestoneEntity milestone = milestone(10L, 100L);

        AssignTaskRequest request = new AssignTaskRequest();
        request.setAssigneeId(7L);

        when(taskRepository.findById(20L)).thenReturn(Optional.of(task));
        when(milestoneRepository.findById(10L)).thenReturn(Optional.of(milestone));
        when(groupMemberRepository.existsByGroupIdAndUserId(100L, 7L)).thenReturn(true);
        when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TaskResponse response = taskService.assign(20L, request);

        assertEquals(7L, response.getAssignedToStudentId());
        assertEquals(20L, response.getId());
        verify(taskRepository).save(task);
    }

    @Test
    void assign_throwsInvalidAssigneeWhenUserDoesNotBelongToProjectGroup() {
        TaskEntity task = task(20L, 10L, null);
        MilestoneEntity milestone = milestone(10L, 100L);

        AssignTaskRequest request = new AssignTaskRequest();
        request.setAssigneeId(7L);

        when(taskRepository.findById(20L)).thenReturn(Optional.of(task));
        when(milestoneRepository.findById(10L)).thenReturn(Optional.of(milestone));
        when(groupMemberRepository.existsByGroupIdAndUserId(100L, 7L)).thenReturn(false);

        assertThrows(InvalidAssigneeException.class, () -> taskService.assign(20L, request));

        verify(taskRepository, never()).save(any());
    }

    @Test
    void getByMilestone_returnsAllKanbanTasksForGroupLeader() {
        User currentStudent = authenticate("student", "STUDENT");
        MilestoneEntity milestone = milestone(10L, 100L);
        TaskEntity first = task(1L, 10L, 7L);
        User assignee = student(7L);
        first.setAssignedToStudent(assignee);
        first.setDescription("Read papers and summarize architecture.");
        first.setDeadline(LocalDate.of(2026, 6, 10));
        first.setProgressPercent(35);
        first.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        TaskEntity second = task(2L, 10L, null);
        second.setStatus(TaskStatus.REVIEW);
        second.setCreatedAt(Instant.parse("2026-01-02T00:00:00Z"));

        when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(10L)).thenReturn(Optional.of(milestone));
        when(groupMemberRepository.findActiveRoleByProjectIdAndUserId(50L, currentStudent.getId()))
                .thenReturn(Optional.of(GroupRole.LEADER));
        when(taskRepository.findByMilestoneIdAndDeletedFalseAndActiveTrueOrderByDeadlineAscCreatedAtAsc(10L))
                .thenReturn(List.of(first, second));

        List<TaskResponse> responses = taskService.getByMilestone(10L);

        assertEquals(2, responses.size());
        assertEquals(1L, responses.get(0).getId());
        assertEquals(50L, responses.get(0).getProjectId());
        assertEquals("Read papers and summarize architecture.", responses.get(0).getDescription());
        assertEquals(7L, responses.get(0).getAssignedToStudentId());
        assertEquals("Student Seven", responses.get(0).getAssignedToStudentName());
        assertEquals("student7@uet.edu.vn", responses.get(0).getAssignedToStudentEmail());
        assertEquals(LocalDate.of(2026, 6, 10), responses.get(0).getDeadline());
        assertEquals(35, responses.get(0).getProgressPercent());
        assertEquals(2L, responses.get(1).getId());
        assertEquals(TaskStatus.WAITING_REVIEW, responses.get(1).getStatus());
        verify(taskRepository).findByMilestoneIdAndDeletedFalseAndActiveTrueOrderByDeadlineAscCreatedAtAsc(10L);
    }

    @Test
    void getByMilestone_returnsTasksForManagerOfMilestoneLab() {
        User manager = authenticate("manager", "LAB_MANAGER");
        Laboratory lab = lab(1L);
        MilestoneEntity milestone = milestone(10L, 100L);
        milestone.getProject().setLab(lab);

        when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(10L)).thenReturn(Optional.of(milestone));
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(manager.getId())).thenReturn(Optional.of(lab));
        when(taskRepository.findByMilestoneIdAndDeletedFalseAndActiveTrueOrderByDeadlineAscCreatedAtAsc(10L))
                .thenReturn(List.of());

        assertTrue(taskService.getByMilestone(10L).isEmpty());
    }

    @Test
    void getByMilestone_returnsOnlyAssignedTasksForRegularMember() {
        User currentStudent = authenticate("student", "STUDENT");
        MilestoneEntity milestone = milestone(10L, 100L);
        TaskEntity assigned = task(1L, 10L, currentStudent.getId());

        when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(10L)).thenReturn(Optional.of(milestone));
        when(groupMemberRepository.findActiveRoleByProjectIdAndUserId(50L, currentStudent.getId()))
                .thenReturn(Optional.of(GroupRole.MEMBER));
        when(taskRepository.findByMilestoneIdAndAssigneeIdAndDeletedFalseAndActiveTrueOrderByDeadlineAscCreatedAtAsc(
                10L,
                currentStudent.getId()
        )).thenReturn(List.of(assigned));

        List<TaskResponse> responses = taskService.getByMilestone(10L);

        assertEquals(1, responses.size());
        assertEquals(currentStudent.getId(), responses.get(0).getAssignedToStudentId());
        verify(taskRepository, never())
                .findByMilestoneIdAndDeletedFalseAndActiveTrueOrderByDeadlineAscCreatedAtAsc(any());
    }

    @Test
    void getByGroup_returnsAllProjectTasksForGroupLeader() {
        User leader = authenticate("leader", "STUDENT");
        GroupEntity group = group(100L, 50L);

        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(100L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findActiveRoleByProjectIdAndUserId(50L, leader.getId()))
                .thenReturn(Optional.of(GroupRole.LEADER));
        when(taskRepository.findBoardTasksByProjectId(50L)).thenReturn(List.of(task(1L, 10L, 7L)));

        List<TaskResponse> responses = taskService.getByGroup(100L);

        assertEquals(1, responses.size());
        assertEquals(1L, responses.get(0).getId());
        verify(taskRepository).findBoardTasksByProjectId(50L);
        verify(taskRepository, never()).findAssignedBoardTasksByProjectId(any(), any());
    }

    @Test
    void getByGroup_returnsOnlyAssignedProjectTasksForMember() {
        User member = authenticate("student", "STUDENT");
        GroupEntity group = group(100L, 50L);

        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(100L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findActiveRoleByProjectIdAndUserId(50L, member.getId()))
                .thenReturn(Optional.of(GroupRole.MEMBER));
        when(taskRepository.findAssignedBoardTasksByProjectId(50L, member.getId()))
                .thenReturn(List.of(task(1L, 10L, member.getId())));

        List<TaskResponse> responses = taskService.getByGroup(100L);

        assertEquals(1, responses.size());
        assertEquals(member.getId(), responses.get(0).getAssignedToStudentId());
        verify(taskRepository).findAssignedBoardTasksByProjectId(50L, member.getId());
        verify(taskRepository, never()).findBoardTasksByProjectId(any());
    }

    @Test
    void getByMilestone_deniesStudentOutsideProjectGroup() {
        User currentStudent = authenticate("student", "STUDENT");
        when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(10L))
                .thenReturn(Optional.of(milestone(10L, 100L)));
        when(groupMemberRepository.findActiveRoleByProjectIdAndUserId(50L, currentStudent.getId()))
                .thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () -> taskService.getByMilestone(10L));
        verify(taskRepository, never())
                .findByMilestoneIdAndDeletedFalseAndActiveTrueOrderByDeadlineAscCreatedAtAsc(any());
    }

    @Test
    void getByMilestone_deniesAdminInAppScope() {
        authenticate("admin", "ADMIN");
        when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(10L))
                .thenReturn(Optional.of(milestone(10L, 100L)));

        assertThrows(AccessDeniedException.class, () -> taskService.getByMilestone(10L));
        verify(taskRepository, never())
                .findByMilestoneIdAndDeletedFalseAndActiveTrueOrderByDeadlineAscCreatedAtAsc(any());
    }

    @Test
    void getByMilestone_throwsWhenMilestoneMissing() {
        when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(10L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> taskService.getByMilestone(10L));
    }

    @Test
    void updateStatus_studentMovesAssignedTodoToDoingAndStartsProgress() {
        User currentStudent = authenticate("student", "STUDENT");
        TaskEntity task = task(20L, 10L, currentStudent.getId());
        MilestoneEntity milestone = milestone(10L, 100L);
        UpdateTaskStatusRequest request = statusRequest(TaskStatus.DOING);

        when(taskRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(task));
        when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(10L)).thenReturn(Optional.of(milestone));
        when(groupMemberRepository.findActiveRoleByProjectIdAndUserId(50L, currentStudent.getId()))
                .thenReturn(Optional.of(GroupRole.MEMBER));
        when(taskRepository.save(task)).thenReturn(task);

        TaskResponse response = taskService.updateStatus(20L, request);

        assertEquals(TaskStatus.DOING, response.getStatus());
        assertEquals(10, response.getProgressPercent());
        verify(taskRepository).save(task);
    }

    @Test
    void updateStatus_studentCannotMoveTaskToDone() {
        User currentStudent = authenticate("student", "STUDENT");
        TaskEntity task = task(20L, 10L, currentStudent.getId());
        task.setStatus(TaskStatus.WAITING_REVIEW);

        when(taskRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(task));
        when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(10L))
                .thenReturn(Optional.of(milestone(10L, 100L)));
        when(groupMemberRepository.findActiveRoleByProjectIdAndUserId(50L, currentStudent.getId()))
                .thenReturn(Optional.of(GroupRole.MEMBER));

        assertThrows(IllegalArgumentException.class,
                () -> taskService.updateStatus(20L, statusRequest(TaskStatus.DONE)));
        verify(taskRepository, never()).save(any());
    }

    @Test
    void updateStatus_studentMovesAssignedRevisionTaskBackToDoing() {
        User currentStudent = authenticate("student", "STUDENT");
        TaskEntity task = task(20L, 10L, currentStudent.getId());
        task.setStatus(TaskStatus.NEEDS_REVISION);
        task.setProgressPercent(5);

        when(taskRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(task));
        when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(10L))
                .thenReturn(Optional.of(milestone(10L, 100L)));
        when(groupMemberRepository.findActiveRoleByProjectIdAndUserId(50L, currentStudent.getId()))
                .thenReturn(Optional.of(GroupRole.MEMBER));
        when(taskRepository.save(task)).thenReturn(task);

        TaskResponse response = taskService.updateStatus(20L, statusRequest(TaskStatus.DOING));

        assertEquals(TaskStatus.DOING, response.getStatus());
        assertEquals(10, response.getProgressPercent());
        verify(taskRepository).save(task);
    }

    @Test
    void updateStatus_studentSubmitsDoingTaskForReviewAtNinetyPercent() {
        User currentStudent = authenticate("student", "STUDENT");
        TaskEntity task = task(20L, 10L, currentStudent.getId());
        task.setStatus(TaskStatus.DOING);
        task.setProgressPercent(35);

        when(taskRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(task));
        when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(10L))
                .thenReturn(Optional.of(milestone(10L, 100L)));
        when(groupMemberRepository.findActiveRoleByProjectIdAndUserId(50L, currentStudent.getId()))
                .thenReturn(Optional.of(GroupRole.MEMBER));
        when(taskRepository.save(task)).thenReturn(task);

        TaskResponse response = taskService.updateStatus(20L, statusRequest(TaskStatus.WAITING_REVIEW));

        assertEquals(TaskStatus.WAITING_REVIEW, response.getStatus());
        assertEquals(90, response.getProgressPercent());
        verify(taskRepository).save(task);
    }

    @Test
    void updateStatus_studentCannotCancelAssignedTask() {
        User currentStudent = authenticate("student", "STUDENT");
        TaskEntity task = task(20L, 10L, currentStudent.getId());

        when(taskRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(task));
        when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(10L))
                .thenReturn(Optional.of(milestone(10L, 100L)));
        when(groupMemberRepository.findActiveRoleByProjectIdAndUserId(50L, currentStudent.getId()))
                .thenReturn(Optional.of(GroupRole.MEMBER));

        assertThrows(IllegalArgumentException.class,
                () -> taskService.updateStatus(20L, statusRequest(TaskStatus.CANCELLED)));
        verify(taskRepository, never()).save(any());
    }

    @Test
    void updateStatus_studentCannotUpdateTaskAssignedToAnotherStudent() {
        User currentStudent = authenticate("student", "STUDENT");
        TaskEntity task = task(20L, 10L, 7L);

        when(taskRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(task));
        when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(10L))
                .thenReturn(Optional.of(milestone(10L, 100L)));
        when(groupMemberRepository.findActiveRoleByProjectIdAndUserId(50L, currentStudent.getId()))
                .thenReturn(Optional.of(GroupRole.MEMBER));

        assertThrows(AccessDeniedException.class,
                () -> taskService.updateStatus(20L, statusRequest(TaskStatus.DOING)));
        verify(taskRepository, never()).save(any());
    }

    @Test
    void updateStatus_studentCannotUpdateTaskFromAnotherProjectGroup() {
        User currentStudent = authenticate("student", "STUDENT");
        TaskEntity task = task(20L, 10L, currentStudent.getId());

        when(taskRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(task));
        when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(10L))
                .thenReturn(Optional.of(milestone(10L, 100L)));
        when(groupMemberRepository.findActiveRoleByProjectIdAndUserId(50L, currentStudent.getId()))
                .thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class,
                () -> taskService.updateStatus(20L, statusRequest(TaskStatus.DOING)));
        verify(taskRepository, never()).save(any());
    }

    @Test
    void updateStatus_groupLeaderMovesTaskAssignedToAnotherMember() {
        User leader = authenticate("leader", "STUDENT");
        TaskEntity task = task(20L, 10L, 7L);
        MilestoneEntity milestone = milestone(10L, 100L);

        when(taskRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(task));
        when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(10L)).thenReturn(Optional.of(milestone));
        when(groupMemberRepository.findActiveRoleByProjectIdAndUserId(50L, leader.getId()))
                .thenReturn(Optional.of(GroupRole.LEADER));
        when(taskRepository.save(task)).thenReturn(task);

        TaskResponse response = taskService.updateStatus(20L, statusRequest(TaskStatus.DOING));

        assertEquals(TaskStatus.DOING, response.getStatus());
        verify(taskRepository).save(task);
    }

    @Test
    void updateStatus_managerCompletesReviewTaskInManagedLab() {
        User manager = authenticate("manager", "LAB_MANAGER");
        Laboratory lab = lab(1L);
        MilestoneEntity milestone = milestone(10L, 100L);
        milestone.getProject().setLab(lab);
        TaskEntity task = task(20L, 10L, 7L);
        task.setStatus(TaskStatus.WAITING_REVIEW);
        task.setProgressPercent(70);

        when(taskRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(task));
        when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(10L)).thenReturn(Optional.of(milestone));
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(manager.getId())).thenReturn(Optional.of(lab));
        when(reportRepository.existsLatestApprovedByTaskId(20L)).thenReturn(true);
        when(taskRepository.save(task)).thenReturn(task);

        TaskResponse response = taskService.updateStatus(20L, statusRequest(TaskStatus.DONE));

        assertEquals(TaskStatus.DONE, response.getStatus());
        assertEquals(100, response.getProgressPercent());
        verify(taskRepository).save(task);
    }

    @Test
    void updateStatus_managerCannotCompleteTaskWithoutApprovedReport() {
        User manager = authenticate("manager", "LAB_MANAGER");
        Laboratory lab = lab(1L);
        MilestoneEntity milestone = milestone(10L, 100L);
        milestone.getProject().setLab(lab);
        TaskEntity task = task(20L, 10L, 7L);
        task.setStatus(TaskStatus.WAITING_REVIEW);

        when(taskRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(task));
        when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(10L)).thenReturn(Optional.of(milestone));
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(manager.getId())).thenReturn(Optional.of(lab));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> taskService.updateStatus(20L, statusRequest(TaskStatus.DONE))
        );

        assertEquals("Cần có báo cáo được duyệt trước khi hoàn thành nhiệm vụ/mốc nghiên cứu.", error.getMessage());
        verify(taskRepository, never()).save(any());
    }

    @Test
    void updateStatus_managerRequestsRevisionWithoutChangingProgress() {
        User manager = authenticate("manager", "LAB_MANAGER");
        Laboratory lab = lab(1L);
        MilestoneEntity milestone = milestone(10L, 100L);
        milestone.getProject().setLab(lab);
        TaskEntity task = task(20L, 10L, 7L);
        task.setStatus(TaskStatus.WAITING_REVIEW);
        task.setProgressPercent(90);

        when(taskRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(task));
        when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(10L)).thenReturn(Optional.of(milestone));
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(manager.getId())).thenReturn(Optional.of(lab));
        when(taskRepository.save(task)).thenReturn(task);

        TaskResponse response = taskService.updateStatus(20L, statusRequest(TaskStatus.NEEDS_REVISION));

        assertEquals(TaskStatus.NEEDS_REVISION, response.getStatus());
        assertEquals(90, response.getProgressPercent());
        verify(taskRepository).save(task);
    }

    @Test
    void updateStatus_managerCannotUpdateTaskOutsideManagedLab() {
        User manager = authenticate("manager", "LAB_MANAGER");
        MilestoneEntity milestone = milestone(10L, 100L);
        milestone.getProject().setLab(lab(2L));

        when(taskRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(task(20L, 10L, 7L)));
        when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(10L)).thenReturn(Optional.of(milestone));
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(manager.getId()))
                .thenReturn(Optional.of(lab(1L)));

        assertThrows(AccessDeniedException.class,
                () -> taskService.updateStatus(20L, statusRequest(TaskStatus.DOING)));
        verify(taskRepository, never()).save(any());
    }

    @Test
    void updateStatus_rejectsManualOverdueTarget() {
        User manager = authenticate("manager", "LAB_MANAGER");
        Laboratory lab = lab(1L);
        MilestoneEntity milestone = milestone(10L, 100L);
        milestone.getProject().setLab(lab);

        when(taskRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(task(20L, 10L, 7L)));
        when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(10L)).thenReturn(Optional.of(milestone));
        when(laboratoryRepository.findFirstByManagerIdAndDeletedFalse(manager.getId())).thenReturn(Optional.of(lab));

        assertThrows(IllegalArgumentException.class,
                () -> taskService.updateStatus(20L, statusRequest(TaskStatus.OVERDUE)));
        verify(taskRepository, never()).save(any());
    }

    private TaskEntity task(Long id, Long milestoneId, Long assigneeId) {
        TaskEntity task = TaskEntity.builder()
                .milestoneId(milestoneId)
                .assigneeId(assigneeId)
                .title("Prepare dataset")
                .status(TaskStatus.TODO)
                .build();
        task.setId(id);
        return task;
    }

    private UpdateTaskStatusRequest statusRequest(TaskStatus status) {
        UpdateTaskStatusRequest request = new UpdateTaskStatusRequest();
        request.setStatus(status);
        return request;
    }

    private MilestoneEntity milestone(Long id, Long groupId) {
        GroupEntity group = new GroupEntity();
        group.setId(groupId);

        ProjectEntity project = new ProjectEntity();
        project.setId(50L);
        project.setGroup(group);

        MilestoneEntity milestone = new MilestoneEntity();
        milestone.setId(id);
        milestone.setProject(project);
        return milestone;
    }

    private GroupEntity group(Long groupId, Long projectId) {
        ProjectEntity project = new ProjectEntity();
        project.setId(projectId);

        GroupEntity group = new GroupEntity();
        group.setId(groupId);
        group.setProject(project);
        return group;
    }

    private User authenticate(String username, String roleName) {
        User user = new User();
        user.setId(roleName.equals("LAB_MANAGER") ? 2L : 3L);
        user.setUsername(username);
        user.setEmail(username + "@uet.edu.vn");
        user.addRole(new Role(roleName, roleName));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, List.of())
        );
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        return user;
    }

    private User student(Long id) {
        User student = new User();
        student.setId(id);
        student.setFullName("Student Seven");
        student.setEmail("student7@uet.edu.vn");
        return student;
    }

    private Laboratory lab(Long id) {
        Laboratory lab = new Laboratory();
        lab.setId(id);
        return lab;
    }
}
