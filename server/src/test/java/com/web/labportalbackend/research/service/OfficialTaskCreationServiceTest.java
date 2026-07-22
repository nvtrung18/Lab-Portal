package com.web.labportalbackend.research.service;

import com.web.labportalbackend.admin.audit.service.AuditLogService;
import com.web.labportalbackend.admin.systemconfig.service.SystemConfigService;
import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.dto.request.AssignTaskRequest;
import com.web.labportalbackend.research.dto.request.CreateResearchTaskRequest;
import com.web.labportalbackend.research.dto.request.UpdateTaskStatusRequest;
import com.web.labportalbackend.research.dto.response.TaskResponse;
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
import com.web.labportalbackend.research.repository.ReportRepository;
import com.web.labportalbackend.research.repository.TaskRepository;
import com.web.labportalbackend.research.security.TaskPermissionHelper;
import com.web.labportalbackend.research.service.impl.TaskServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OfficialTaskCreationServiceTest {

    @Mock TaskRepository taskRepository;
    @Mock MilestoneRepository milestoneRepository;
    @Mock ReportRepository reportRepository;
    @Mock GroupRepository groupRepository;
    @Mock GroupMemberRepository groupMemberRepository;
    @Mock LaboratoryRepository laboratoryRepository;
    @Mock UserRepository userRepository;
    @Mock SystemConfigService systemConfigService;
    @Mock ProjectRepository projectRepository;
    @Mock TaskPermissionHelper taskPermissionHelper;
    @Mock AuditLogService auditLogService;
    @InjectMocks TaskServiceImpl service;

    private User manager;
    private Laboratory lab;
    private ProjectEntity project;

    @BeforeEach
    void setUp() {
        manager = user(2L, "manager", "LAB_MANAGER");
        lab = lab(1L);
        project = ProjectEntity.builder().lab(lab).title("Project").build();
        project.setId(50L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("manager", null, List.of()));
        lenient().when(userRepository.findByUsername("manager")).thenReturn(Optional.of(manager));
        lenient().when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(50L)).thenReturn(Optional.of(project));
        lenient().when(taskPermissionHelper.canCreateOfficialTask(2L, project)).thenReturn(true);
        lenient().when(taskRepository.save(any(TaskEntity.class))).thenAnswer(invocation -> {
            TaskEntity task = invocation.getArgument(0);
            task.setId(20L);
            return task;
        });
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsGroupBacklogWithActiveMemberAndExplicitPriorityType() {
        GroupEntity group = group(100L, project, lab);
        User assignee = user(7L, "student", "STUDENT");
        CreateResearchTaskRequest request = request();
        request.setGroupId(100L);
        request.setAssigneeId(7L);
        request.setPriority(TaskPriority.HIGH);
        request.setType(TaskType.REVIEW);
        request.setDueDate(LocalDate.of(2026, 9, 1));
        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(100L)).thenReturn(Optional.of(group));
        when(userRepository.findById(7L)).thenReturn(Optional.of(assignee));
        when(groupMemberRepository.existsByGroupIdAndUserIdAndActiveTrueAndDeletedFalse(100L, 7L)).thenReturn(true);

        TaskResponse response = service.createResearchTask(request);

        assertEquals(TaskStatus.BACKLOG, response.getStatus());
        assertEquals(100L, response.getGroupId());
        assertEquals(7L, response.getAssignedToStudentId());
        assertEquals(TaskPriority.HIGH, response.getPriority());
        assertEquals(TaskType.REVIEW, response.getType());
        assertEquals(response.getDueDate(), response.getDeadline());
    }

    @Test
    void createsMilestoneTaskAsTodoAndAllowsProjectMilestoneWithValidGroup() {
        GroupEntity group = group(100L, project, lab);
        MilestoneEntity milestone = MilestoneEntity.builder().project(project).title("M1").build();
        milestone.setId(10L);
        CreateResearchTaskRequest request = request();
        request.setGroupId(100L);
        request.setMilestoneId(10L);
        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(100L)).thenReturn(Optional.of(group));
        when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(10L)).thenReturn(Optional.of(milestone));

        TaskResponse response = service.createResearchTask(request);

        assertEquals(TaskStatus.TODO, response.getStatus());
        assertEquals(10L, response.getMilestoneId());
        assertEquals(100L, response.getGroupId());
    }

    @Test
    void createsGroupBacklogThroughLegacyProjectGroupMapping() {
        GroupEntity legacyGroup = group(100L, null, lab);
        project.setGroup(legacyGroup);
        CreateResearchTaskRequest request = request();
        request.setGroupId(100L);
        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(100L)).thenReturn(Optional.of(legacyGroup));

        TaskResponse response = service.createResearchTask(request);

        assertEquals(100L, response.getGroupId());
        assertEquals(TaskStatus.BACKLOG, response.getStatus());
    }

    @Test
    void rejectsAssigneeWithoutGroupBeforeLoadingAssignee() {
        CreateResearchTaskRequest request = request();
        request.setAssigneeId(7L);

        assertThrows(IllegalArgumentException.class, () -> service.createResearchTask(request));

        verify(userRepository, never()).findById(7L);
        verifyNoPersistence();
    }

    @Test
    void rejectsMissingReferencesWithoutPersistence() {
        CreateResearchTaskRequest missingProject = request();
        when(projectRepository.findByIdAndDeletedFalseAndActiveTrue(50L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.createResearchTask(missingProject));
        verifyNoPersistence();
    }

    @Test
    void rejectsMissingGroupMilestoneParentAndAssignee() {
        CreateResearchTaskRequest missingGroup = request();
        missingGroup.setGroupId(100L);
        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(100L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.createResearchTask(missingGroup));

        GroupEntity group = group(100L, project, lab);
        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(100L)).thenReturn(Optional.of(group));
        CreateResearchTaskRequest missingMilestone = request();
        missingMilestone.setGroupId(100L);
        missingMilestone.setMilestoneId(10L);
        when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(10L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.createResearchTask(missingMilestone));

        CreateResearchTaskRequest missingParent = request();
        missingParent.setGroupId(100L);
        missingParent.setParentTaskId(5L);
        when(taskRepository.findByIdAndDeletedFalseAndActiveTrue(5L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.createResearchTask(missingParent));

        CreateResearchTaskRequest missingAssignee = request();
        missingAssignee.setGroupId(100L);
        missingAssignee.setAssigneeId(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.createResearchTask(missingAssignee));

        verifyNoPersistence();
    }

    @Test
    void rejectsCrossProjectOrCrossLabGroup() {
        ProjectEntity otherProject = ProjectEntity.builder().lab(lab).title("Other").build();
        otherProject.setId(60L);
        GroupEntity wrongProject = group(100L, otherProject, lab);
        CreateResearchTaskRequest request = request();
        request.setGroupId(100L);
        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(100L)).thenReturn(Optional.of(wrongProject));
        assertThrows(IllegalArgumentException.class, () -> service.createResearchTask(request));

        project.setGroup(wrongProject);
        assertThrows(IllegalArgumentException.class, () -> service.createResearchTask(request));
        project.setGroup(null);

        GroupEntity wrongLab = group(100L, project, lab(2L));
        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(100L)).thenReturn(Optional.of(wrongLab));
        assertThrows(IllegalArgumentException.class, () -> service.createResearchTask(request));
        verifyNoPersistence();
    }

    @Test
    void rejectsCrossProjectAndGroupScopedMilestones() {
        GroupEntity group = group(100L, project, lab);
        CreateResearchTaskRequest request = request();
        request.setGroupId(100L);
        request.setMilestoneId(10L);
        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(100L)).thenReturn(Optional.of(group));

        ProjectEntity other = ProjectEntity.builder().lab(lab).title("Other").build();
        other.setId(60L);
        MilestoneEntity crossProject = MilestoneEntity.builder().project(other).build();
        crossProject.setId(10L);
        when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(10L)).thenReturn(Optional.of(crossProject));
        assertThrows(IllegalArgumentException.class, () -> service.createResearchTask(request));

        GroupEntity otherGroup = group(101L, project, lab);
        MilestoneEntity scoped = MilestoneEntity.builder().project(project).group(otherGroup).build();
        scoped.setId(10L);
        when(milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(10L)).thenReturn(Optional.of(scoped));
        assertThrows(IllegalArgumentException.class, () -> service.createResearchTask(request));
        verifyNoPersistence();
    }

    @Test
    void rejectsParentWithDifferentProjectOrGroupScope() {
        GroupEntity group = group(100L, project, lab);
        CreateResearchTaskRequest request = request();
        request.setGroupId(100L);
        request.setParentTaskId(5L);
        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(100L)).thenReturn(Optional.of(group));

        TaskEntity parent = TaskEntity.builder().projectId(60L).groupId(100L).title("Parent").build();
        parent.setId(5L);
        when(taskRepository.findByIdAndDeletedFalseAndActiveTrue(5L)).thenReturn(Optional.of(parent));
        assertThrows(IllegalArgumentException.class, () -> service.createResearchTask(request));

        parent.setProjectId(50L);
        parent.setGroupId(null);
        assertThrows(IllegalArgumentException.class, () -> service.createResearchTask(request));
        verifyNoPersistence();
    }

    @Test
    void rejectsAssigneeWithoutActiveMembershipButDoesNotRequireStudentRole() {
        GroupEntity group = group(100L, project, lab);
        User assignee = user(7L, "researcher", "ADMIN");
        CreateResearchTaskRequest request = request();
        request.setGroupId(100L);
        request.setAssigneeId(7L);
        when(groupRepository.findByIdAndDeletedFalseAndActiveTrue(100L)).thenReturn(Optional.of(group));
        when(userRepository.findById(7L)).thenReturn(Optional.of(assignee));
        when(groupMemberRepository.existsByGroupIdAndUserIdAndActiveTrueAndDeletedFalse(100L, 7L))
                .thenReturn(false, true);

        assertThrows(IllegalArgumentException.class, () -> service.createResearchTask(request));
        TaskResponse response = service.createResearchTask(request);

        assertEquals(7L, response.getAssignedToStudentId());
    }

    @Test
    void rejectsInactiveManagerBeforeLoadingProject() {
        manager.setActive(false);

        assertThrows(AccessDeniedException.class, () -> service.createResearchTask(request()));

        verify(projectRepository, never()).findByIdAndDeletedFalseAndActiveTrue(any());
        verifyNoPersistence();
    }

    @Test
    void legacyStatusAndAssignRejectNullMilestoneWithoutRepositoryNullLookup() {
        TaskEntity task = TaskEntity.builder().projectId(50L).title("Backlog").milestoneId(null).build();
        task.setId(20L);
        when(taskRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(task));
        when(taskRepository.findById(20L)).thenReturn(Optional.of(task));
        UpdateTaskStatusRequest status = new UpdateTaskStatusRequest();
        status.setStatus(TaskStatus.TODO);
        AssignTaskRequest assign = new AssignTaskRequest();
        assign.setAssigneeId(7L);

        assertThrows(IllegalArgumentException.class, () -> service.updateStatus(20L, status));
        assertThrows(IllegalArgumentException.class, () -> service.assign(20L, assign));

        verifyNoInteractions(milestoneRepository);
        verify(taskRepository, never()).save(any());
    }

    private CreateResearchTaskRequest request() {
        CreateResearchTaskRequest request = new CreateResearchTaskRequest();
        request.setProjectId(50L);
        request.setTitle("Official task");
        return request;
    }

    private GroupEntity group(Long id, ProjectEntity project, Laboratory groupLab) {
        GroupEntity group = GroupEntity.builder().project(project).lab(groupLab).name("Group").build();
        group.setId(id);
        return group;
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
        Laboratory lab = new Laboratory();
        lab.setId(id);
        return lab;
    }

    private void verifyNoPersistence() {
        verify(taskRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }
}
