package com.web.labportalbackend.research.service;

import com.web.labportalbackend.admin.audit.service.AuditLogService;
import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.dto.request.PatchResearchTaskRequest;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskMetadataPatchCycleTest {

    @Mock TaskRepository taskRepository;
    @Mock ProjectRepository projectRepository;
    @Mock GroupRepository groupRepository;
    @Mock MilestoneRepository milestoneRepository;
    @Mock UserRepository userRepository;
    @Mock GroupMemberRepository groupMemberRepository;
    @Mock LaboratoryRepository laboratoryRepository;
    @Mock AuditLogService auditLogService;
    @InjectMocks TaskMetadataPatchService service;

    private TaskEntity task;

    @BeforeEach
    void setUp() {
        User manager = new User();
        manager.setId(2L);
        manager.setUsername("manager");
        manager.addRole(new Role("LAB_MANAGER", "manager"));
        Laboratory lab = new Laboratory();
        lab.setId(1L);
        ProjectEntity project = ProjectEntity.builder().lab(lab).title("Project").build();
        project.setId(50L);
        task = task(20L, null);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("manager", null, List.of()));
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
    void rejectsSelfParentWithoutParentLookup() {
        assertThrows(IllegalArgumentException.class, () -> service.patch(20L, parent(20L)));

        verify(taskRepository, times(1)).findByIdForUpdate(20L);
        verify(taskRepository, never()).save(any());
    }

    @Test
    void rejectsDirectAndIndirectCycles() {
        TaskEntity direct = task(5L, 20L);
        when(taskRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(direct));
        assertThrows(IllegalArgumentException.class, () -> service.patch(20L, parent(5L)));

        TaskEntity first = task(6L, 7L);
        TaskEntity second = task(7L, 20L);
        when(taskRepository.findByIdForUpdate(6L)).thenReturn(Optional.of(first));
        when(taskRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(second));
        assertThrows(IllegalArgumentException.class, () -> service.patch(20L, parent(6L)));

        verify(taskRepository, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void rejectsCorruptExistingAncestorCycle() {
        TaskEntity first = task(5L, 6L);
        TaskEntity second = task(6L, 5L);
        when(taskRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(first));
        when(taskRepository.findByIdForUpdate(6L)).thenReturn(Optional.of(second));

        assertThrows(IllegalArgumentException.class, () -> service.patch(20L, parent(5L)));
        verify(taskRepository, never()).save(any());
    }

    @Test
    void rejectsMissingOrInactiveAncestor() {
        TaskEntity first = task(5L, 6L);
        when(taskRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(first));
        when(taskRepository.findByIdForUpdate(6L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.patch(20L, parent(5L)));
        verify(taskRepository, never()).save(any());
    }

    @Test
    void acceptsValidLockedAncestorChain() {
        TaskEntity first = task(5L, 6L);
        TaskEntity root = task(6L, null);
        when(taskRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(first));
        when(taskRepository.findByIdForUpdate(6L)).thenReturn(Optional.of(root));

        service.patch(20L, parent(5L));

        assertEquals(5L, task.getParentTaskId());
        verify(taskRepository).save(task);
        verify(auditLogService).log(any(), any(), any(), eq("RESEARCH_TASK"), eq(20L), anyString(),
                eq("{\"changedFields\":[\"parentTaskId\"]}"));
    }

    @Test
    void rejectsAncestorOutsideFinalProjectOrGroupScope() {
        TaskEntity crossProject = task(5L, null);
        crossProject.setProjectId(60L);
        when(taskRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(crossProject));

        assertThrows(IllegalArgumentException.class, () -> service.patch(20L, parent(5L)));
        verify(taskRepository, never()).save(any());
    }

    private PatchResearchTaskRequest parent(Long id) {
        PatchResearchTaskRequest request = new PatchResearchTaskRequest();
        request.setParentTaskId(id);
        return request;
    }

    private TaskEntity task(Long id, Long parentId) {
        TaskEntity entity = TaskEntity.builder()
                .projectId(50L)
                .parentTaskId(parentId)
                .title("Task " + id)
                .status(TaskStatus.BACKLOG)
                .priority(TaskPriority.MEDIUM)
                .type(TaskType.TASK)
                .build();
        entity.setId(id);
        return entity;
    }
}
