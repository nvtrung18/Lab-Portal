package com.web.labportalbackend.research.service.impl;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.exception.InvalidAssigneeException;
import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final MilestoneRepository milestoneRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final UserRepository userRepository;

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
        MilestoneEntity milestone = milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", milestoneId));
        assertCanViewTasks(getCurrentUser(), milestone);

        return taskRepository.findByMilestoneIdAndDeletedFalseAndActiveTrueOrderByDeadlineAscCreatedAtAsc(milestoneId)
                .stream()
                .map(task -> TaskMapper.toResponse(task, milestone.getProject().getId()))
                .toList();
    }

    private void assertCanViewTasks(User currentUser, MilestoneEntity milestone) {
        if (currentUser.hasRole("LAB_MANAGER")) {
            Laboratory managedLab = laboratoryRepository.findFirstByManagerIdAndDeletedFalse(currentUser.getId())
                    .orElseThrow(() -> new AccessDeniedException("Lab manager is not assigned to any lab"));
            if (milestone.getProject().getLab() != null
                    && managedLab.getId().equals(milestone.getProject().getLab().getId())) {
                return;
            }
            throw new AccessDeniedException("Cannot access tasks from another lab");
        }

        if (currentUser.hasRole("STUDENT")
                && groupMemberRepository.existsActiveMemberByProjectIdAndUserId(
                        milestone.getProject().getId(), currentUser.getId())) {
            return;
        }

        throw new AccessDeniedException("Cannot access tasks for this milestone");
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication is required");
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", authentication.getName()));
    }
}
