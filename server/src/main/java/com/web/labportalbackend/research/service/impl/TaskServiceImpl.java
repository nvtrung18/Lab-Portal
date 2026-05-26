package com.web.labportalbackend.research.service.impl;

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
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private static final Map<TaskStatus, Set<TaskStatus>> MANAGER_STATUS_TRANSITIONS = Map.of(
            TaskStatus.TODO, Set.of(TaskStatus.DOING, TaskStatus.CANCELLED),
            TaskStatus.DOING, Set.of(TaskStatus.WAITING_REVIEW, TaskStatus.CANCELLED),
            TaskStatus.WAITING_REVIEW, Set.of(TaskStatus.DONE, TaskStatus.NEEDS_REVISION, TaskStatus.CANCELLED),
            TaskStatus.NEEDS_REVISION, Set.of(TaskStatus.DOING, TaskStatus.CANCELLED),
            TaskStatus.OVERDUE, Set.of(TaskStatus.DOING, TaskStatus.WAITING_REVIEW,
                    TaskStatus.NEEDS_REVISION, TaskStatus.DONE, TaskStatus.CANCELLED)
    );
    private static final Map<TaskStatus, Set<TaskStatus>> STUDENT_STATUS_TRANSITIONS = Map.of(
            TaskStatus.TODO, Set.of(TaskStatus.DOING),
            TaskStatus.DOING, Set.of(TaskStatus.WAITING_REVIEW),
            TaskStatus.NEEDS_REVISION, Set.of(TaskStatus.DOING)
    );

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

    @Override
    @Transactional
    public TaskResponse updateStatus(Long taskId, UpdateTaskStatusRequest request) {
        TaskEntity task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
        MilestoneEntity milestone = milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(task.getMilestoneId())
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", task.getMilestoneId()));
        User currentUser = getCurrentUser();

        assertCanViewTasks(currentUser, milestone);
        assertCanUpdateTaskStatus(currentUser, task);

        TaskStatus currentStatus = toWorkflowStatus(task.getStatus());
        TaskStatus requestedStatus = request.getStatus();
        validateStatusTarget(requestedStatus);

        if (currentStatus == requestedStatus) {
            return TaskMapper.toResponse(task, milestone.getProject().getId());
        }

        Map<TaskStatus, Set<TaskStatus>> allowedTransitions = currentUser.hasRole("LAB_MANAGER")
                ? MANAGER_STATUS_TRANSITIONS
                : STUDENT_STATUS_TRANSITIONS;
        if (!allowedTransitions.getOrDefault(currentStatus, Set.of()).contains(requestedStatus)) {
            throw new IllegalArgumentException(
                    "Status transition from " + currentStatus + " to " + requestedStatus + " is not allowed");
        }

        task.setStatus(requestedStatus);
        applyStatusProgress(task, requestedStatus);
        return TaskMapper.toResponse(taskRepository.save(task), milestone.getProject().getId());
    }

    private void assertCanUpdateTaskStatus(User currentUser, TaskEntity task) {
        if (currentUser.hasRole("LAB_MANAGER")) {
            return;
        }

        if (currentUser.hasRole("STUDENT")
                && currentUser.getId().equals(task.getAssigneeId())) {
            return;
        }

        throw new AccessDeniedException("Students may update only their assigned tasks");
    }

    private void validateStatusTarget(TaskStatus status) {
        if (status == TaskStatus.IN_PROGRESS || status == TaskStatus.REVIEW || status == TaskStatus.OVERDUE) {
            throw new IllegalArgumentException("Unsupported task status target");
        }
    }

    private TaskStatus toWorkflowStatus(TaskStatus status) {
        if (status == TaskStatus.IN_PROGRESS) {
            return TaskStatus.DOING;
        }
        if (status == TaskStatus.REVIEW) {
            return TaskStatus.WAITING_REVIEW;
        }
        return status;
    }

    private void applyStatusProgress(TaskEntity task, TaskStatus requestedStatus) {
        if (requestedStatus == TaskStatus.DOING
                && (task.getProgressPercent() == null || task.getProgressPercent() < 10)) {
            task.setProgressPercent(10);
        }
        if (requestedStatus == TaskStatus.WAITING_REVIEW) {
            task.setProgressPercent(90);
        }
        if (requestedStatus == TaskStatus.DONE) {
            task.setProgressPercent(100);
        }
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
