package com.web.labportalbackend.research.service.impl;

import com.web.labportalbackend.common.exception.InvalidAssigneeException;
import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.research.dto.request.AssignTaskRequest;
import com.web.labportalbackend.research.dto.request.CreateTaskRequest;
import com.web.labportalbackend.research.dto.response.TaskResponse;
import com.web.labportalbackend.research.entity.MilestoneEntity;
import com.web.labportalbackend.research.entity.TaskEntity;
import com.web.labportalbackend.research.enums.TaskStatus;
import com.web.labportalbackend.research.mapper.TaskMapper;
import com.web.labportalbackend.research.repository.GroupMemberRepository;
import com.web.labportalbackend.research.repository.MilestoneRepository;
import com.web.labportalbackend.research.repository.TaskRepository;
import com.web.labportalbackend.research.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final MilestoneRepository milestoneRepository;
    private final GroupMemberRepository groupMemberRepository;

    @Override
    @Transactional
    public TaskResponse createTask(CreateTaskRequest request) {
        if (!milestoneRepository.existsById(request.getMilestoneId())) {
            throw new ResourceNotFoundException("Milestone", request.getMilestoneId());
        }

        TaskEntity task = TaskEntity.builder()
                .milestoneId(request.getMilestoneId())
                .title(request.getTitle())
                .status(TaskStatus.TODO)
                .build();

        return TaskMapper.toResponse(taskRepository.save(task));
    }

    @Override
    @Transactional
    public TaskResponse assign(Long taskId, AssignTaskRequest request) {
        TaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
        MilestoneEntity milestone = milestoneRepository.findById(task.getMilestoneId())
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", task.getMilestoneId()));

        Long groupId = milestone.getProject().getGroup().getId();
        boolean memberExists = groupMemberRepository.existsByGroupIdAndUserId(groupId, request.getAssigneeId());
        if (!memberExists) {
            throw new InvalidAssigneeException(request.getAssigneeId(), groupId);
        }

        task.setAssigneeId(request.getAssigneeId());
        return TaskMapper.toResponse(taskRepository.save(task));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskResponse> getByMilestone(Long milestoneId) {
        if (!milestoneRepository.existsById(milestoneId)) {
            throw new ResourceNotFoundException("Milestone", milestoneId);
        }
        return taskRepository.findByMilestoneIdOrderByCreatedAtAsc(milestoneId)
                .stream()
                .map(TaskMapper::toResponse)
                .toList();
    }
}
