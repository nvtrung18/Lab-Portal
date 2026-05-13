package com.web.labportalbackend.research.service;

import com.web.labportalbackend.common.exception.InvalidAssigneeException;
import com.web.labportalbackend.research.dto.request.AssignTaskRequest;
import com.web.labportalbackend.research.dto.request.CreateTaskRequest;
import com.web.labportalbackend.research.dto.response.TaskResponse;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.MilestoneEntity;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.entity.TaskEntity;
import com.web.labportalbackend.research.enums.TaskStatus;
import com.web.labportalbackend.research.repository.GroupMemberRepository;
import com.web.labportalbackend.research.repository.MilestoneRepository;
import com.web.labportalbackend.research.repository.TaskRepository;
import com.web.labportalbackend.research.service.impl.TaskServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
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
    private GroupMemberRepository groupMemberRepository;

    @InjectMocks
    private TaskServiceImpl taskService;

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
        assertNull(response.getAssigneeId());

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

        assertEquals(7L, response.getAssigneeId());
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
    void getByMilestone_returnsTasksForMilestone() {
        TaskEntity first = task(1L, 10L, 7L);
        first.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        TaskEntity second = task(2L, 10L, null);
        second.setCreatedAt(Instant.parse("2026-01-02T00:00:00Z"));

        when(milestoneRepository.existsById(10L)).thenReturn(true);
        when(taskRepository.findByMilestoneIdOrderByCreatedAtAsc(10L)).thenReturn(List.of(first, second));

        List<TaskResponse> responses = taskService.getByMilestone(10L);

        assertEquals(2, responses.size());
        assertEquals(1L, responses.get(0).getId());
        assertEquals(2L, responses.get(1).getId());
        verify(taskRepository).findByMilestoneIdOrderByCreatedAtAsc(10L);
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
}
