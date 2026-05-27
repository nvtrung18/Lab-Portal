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
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.TaskEntity;
import com.web.labportalbackend.research.enums.GroupRole;
import com.web.labportalbackend.research.enums.TaskStatus;
import com.web.labportalbackend.research.mapper.TaskMapper;
import com.web.labportalbackend.research.repository.GroupMemberRepository;
import com.web.labportalbackend.research.repository.GroupRepository;
import com.web.labportalbackend.research.repository.MilestoneRepository;
import com.web.labportalbackend.research.repository.ReportRepository;
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

    private static final String APPROVED_REPORT_REQUIRED =
            "Cần có báo cáo được duyệt trước khi hoàn thành nhiệm vụ/mốc nghiên cứu.";
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
    private final ReportRepository reportRepository;
    private final GroupRepository groupRepository;
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
        User currentUser = getCurrentUser();
        assertCanViewTasks(currentUser, milestone);

        List<TaskEntity> visibleTasks = currentUser.hasRole("STUDENT")
                && resolveStudentProjectRole(currentUser, milestone.getProject().getId()) == GroupRole.MEMBER
                ? taskRepository.findByMilestoneIdAndAssigneeIdAndDeletedFalseAndActiveTrueOrderByDeadlineAscCreatedAtAsc(
                        milestoneId,
                        currentUser.getId()
                )
                : taskRepository.findByMilestoneIdAndDeletedFalseAndActiveTrueOrderByDeadlineAscCreatedAtAsc(milestoneId);

        return visibleTasks
                .stream()
                .map(task -> TaskMapper.toResponse(task, milestone.getProject().getId()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskResponse> getByGroup(Long groupId) {
        GroupEntity group = groupRepository.findByIdAndDeletedFalseAndActiveTrue(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Research group", groupId));
        if (group.getProject() == null) {
            throw new AccessDeniedException("Research group is not assigned to a project");
        }

        User currentUser = getCurrentUser();
        Long projectId = group.getProject().getId();
        if (currentUser.hasRole("LAB_MANAGER")) {
            assertManagerCanAccessProject(currentUser, group.getProject());
            return taskRepository.findBoardTasksByProjectId(projectId).stream()
                    .map(task -> TaskMapper.toResponse(task, projectId))
                    .toList();
        }

        GroupRole groupRole = resolveStudentProjectRole(currentUser, projectId);
        List<TaskEntity> visibleTasks = groupRole == GroupRole.LEADER
                ? taskRepository.findBoardTasksByProjectId(projectId)
                : taskRepository.findAssignedBoardTasksByProjectId(projectId, currentUser.getId());
        return visibleTasks.stream()
                .map(task -> TaskMapper.toResponse(task, projectId))
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
        if (requestedStatus == TaskStatus.DONE) {
            assertApprovedReportForCompletion(task);
        }

        task.setStatus(requestedStatus);
        applyStatusProgress(task, requestedStatus);
        return TaskMapper.toResponse(taskRepository.save(task), milestone.getProject().getId());
    }

    private void assertCanUpdateTaskStatus(User currentUser, TaskEntity task) {
        if (currentUser.hasRole("LAB_MANAGER")) {
            return;
        }

        if (currentUser.hasRole("STUDENT")) {
            MilestoneEntity milestone = milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(task.getMilestoneId())
                    .orElseThrow(() -> new ResourceNotFoundException("Milestone", task.getMilestoneId()));
            GroupRole role = resolveStudentProjectRole(currentUser, milestone.getProject().getId());
            if (role == GroupRole.LEADER || currentUser.getId().equals(task.getAssigneeId())) {
                return;
            }
        }

        throw new AccessDeniedException("Members may update only their assigned tasks");
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

    private void assertApprovedReportForCompletion(TaskEntity task) {
        if (!reportRepository.existsLatestApprovedByTaskId(task.getId())) {
            throw new IllegalArgumentException(APPROVED_REPORT_REQUIRED);
        }
    }

    private void assertCanViewTasks(User currentUser, MilestoneEntity milestone) {
        if (currentUser.hasRole("LAB_MANAGER")) {
            assertManagerCanAccessProject(currentUser, milestone.getProject());
            return;
        }

        if (currentUser.hasRole("STUDENT")) {
            resolveStudentProjectRole(currentUser, milestone.getProject().getId());
            return;
        }

        throw new AccessDeniedException("Cannot access tasks for this milestone");
    }

    private void assertManagerCanAccessProject(User currentUser, com.web.labportalbackend.research.entity.ProjectEntity project) {
        Laboratory managedLab = laboratoryRepository.findFirstByManagerIdAndDeletedFalse(currentUser.getId())
                .orElseThrow(() -> new AccessDeniedException("Lab manager is not assigned to any lab"));
        if (project.getLab() == null || !managedLab.getId().equals(project.getLab().getId())) {
            throw new AccessDeniedException("Cannot access tasks from another lab");
        }
    }

    private GroupRole resolveStudentProjectRole(User currentUser, Long projectId) {
        if (!currentUser.hasRole("STUDENT")) {
            throw new AccessDeniedException("Only students have research group roles");
        }
        return groupMemberRepository.findActiveRoleByProjectIdAndUserId(projectId, currentUser.getId())
                .orElseThrow(() -> new AccessDeniedException("Cannot access tasks for this project"));
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
