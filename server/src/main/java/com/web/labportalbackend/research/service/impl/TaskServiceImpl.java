package com.web.labportalbackend.research.service.impl;

import com.web.labportalbackend.admin.audit.enums.AuditAction;
import com.web.labportalbackend.admin.audit.enums.AuditModule;
import com.web.labportalbackend.admin.audit.service.AuditLogService;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.exception.InvalidAssigneeException;
import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.dto.request.AssignTaskRequest;
import com.web.labportalbackend.research.dto.request.CreateResearchTaskRequest;
import com.web.labportalbackend.research.dto.request.CreateTaskRequest;
import com.web.labportalbackend.research.dto.request.PatchResearchTaskRequest;
import com.web.labportalbackend.research.dto.request.PatchTaskStatusRequest;
import com.web.labportalbackend.research.dto.request.UpdateTaskStatusRequest;
import com.web.labportalbackend.research.dto.response.TaskResponse;
import com.web.labportalbackend.research.entity.MilestoneEntity;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.TaskEntity;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.admin.systemconfig.dto.SystemConfigResponse;
import com.web.labportalbackend.admin.systemconfig.service.SystemConfigService;
import com.web.labportalbackend.research.entity.ReportEntity;
import com.web.labportalbackend.research.enums.ReportStatus;
import com.web.labportalbackend.research.enums.GroupRole;
import com.web.labportalbackend.research.enums.TaskPriority;
import com.web.labportalbackend.research.enums.TaskStatus;
import com.web.labportalbackend.research.enums.TaskType;
import com.web.labportalbackend.research.mapper.TaskMapper;
import com.web.labportalbackend.research.repository.GroupMemberRepository;
import com.web.labportalbackend.research.repository.GroupRepository;
import com.web.labportalbackend.research.repository.MilestoneRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.repository.ReportRepository;
import com.web.labportalbackend.research.repository.TaskRepository;
import com.web.labportalbackend.research.service.TaskService;
import com.web.labportalbackend.research.security.TaskPermissionHelper;
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
            TaskStatus.BACKLOG, Set.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.CANCELLED),
            TaskStatus.TODO, Set.of(TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED, TaskStatus.CANCELLED),
            TaskStatus.IN_PROGRESS, Set.of(TaskStatus.IN_REVIEW, TaskStatus.BLOCKED, TaskStatus.CANCELLED),
            TaskStatus.IN_REVIEW, Set.of(TaskStatus.DONE, TaskStatus.NEEDS_REVISION, TaskStatus.BLOCKED, TaskStatus.CANCELLED),
            TaskStatus.NEEDS_REVISION, Set.of(TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED, TaskStatus.CANCELLED),
            TaskStatus.BLOCKED, Set.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.CANCELLED)
    );
    private static final Map<TaskStatus, Set<TaskStatus>> STUDENT_STATUS_TRANSITIONS = Map.of(
            TaskStatus.TODO, Set.of(TaskStatus.IN_PROGRESS),
            TaskStatus.IN_PROGRESS, Set.of(TaskStatus.IN_REVIEW, TaskStatus.BLOCKED),
            TaskStatus.NEEDS_REVISION, Set.of(TaskStatus.IN_PROGRESS),
            TaskStatus.BLOCKED, Set.of(TaskStatus.IN_PROGRESS)
    );

    private final TaskRepository taskRepository;
    private final MilestoneRepository milestoneRepository;
    private final ReportRepository reportRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final LaboratoryRepository laboratoryRepository;
    private final UserRepository userRepository;
    private final SystemConfigService systemConfigService;
    private final ProjectRepository projectRepository;
    private final TaskPermissionHelper taskPermissionHelper;
    private final AuditLogService auditLogService;
    private final TaskMetadataPatchService taskMetadataPatchService;
    private final TaskStatusUpdateService taskStatusUpdateService;

    @Override
    @Transactional
    public TaskResponse createTask(CreateTaskRequest request) {
        MilestoneEntity milestone = milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(request.getMilestoneId())
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", request.getMilestoneId()));

        User currentUser = getCurrentUser();
        ProjectEntity project = milestone.getProject();
        if (!currentUser.hasRole("LAB_MANAGER")) {
            throw new AccessDeniedException("Only lab managers can create tasks");
        }
        assertManagerCanAccessProject(currentUser, project);

        if (request.getAssignedToStudentId() == null) {
            throw new IllegalArgumentException("Assigned student ID is required");
        }

        User student = userRepository.findById(request.getAssignedToStudentId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.getAssignedToStudentId()));

        Long groupId = milestone.getGroup() != null ? milestone.getGroup().getId() : (project.getGroup() != null ? project.getGroup().getId() : null);
        if (groupId == null) {
            throw new IllegalArgumentException("Milestone does not belong to any research group");
        }

        boolean activeInGroup = groupMemberRepository.existsByGroupIdAndUserIdAndActiveTrueAndDeletedFalse(
                groupId,
                request.getAssignedToStudentId()
        );
        if (!student.hasRole("STUDENT") || !activeInGroup) {
            throw new AccessDeniedException("Assignee must be an active student member of this research group");
        }

        TaskEntity task = TaskEntity.builder()
                .projectId(project.getId())
                .groupId(groupId)
                .milestoneId(request.getMilestoneId())
                .assigneeId(request.getAssignedToStudentId())
                .title(request.getTitle().trim())
                .description(request.getDescription())
                .deadline(request.getDeadline())
                .dueDate(request.getDeadline())
                .status(TaskStatus.TODO)
                .createdBy(currentUser.getId())
                .progressPercent(0)
                .build();

        return TaskMapper.toResponse(taskRepository.save(task), project.getId());
    }

    @Override
    @Transactional
    public TaskResponse createResearchTask(CreateResearchTaskRequest request) {
        User currentUser = getUsableCurrentUser();
        if (!currentUser.hasRole("LAB_MANAGER")) {
            throw new AccessDeniedException("Only lab managers can create official tasks");
        }

        ProjectEntity project = projectRepository.findByIdAndDeletedFalseAndActiveTrue(request.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project", request.getProjectId()));
        if (!taskPermissionHelper.canCreateOfficialTask(currentUser.getId(), project)) {
            throw new AccessDeniedException("Cannot create official tasks for this project");
        }
        if (request.getAssigneeId() != null && request.getGroupId() == null) {
            throw new IllegalArgumentException("Project-level tasks cannot have an assignee");
        }

        GroupEntity group = resolveCreateGroup(request.getGroupId(), project);
        MilestoneEntity milestone = resolveCreateMilestone(request.getMilestoneId(), request.getGroupId(), project);
        TaskEntity parent = resolveCreateParent(request.getParentTaskId(), request.getProjectId(), request.getGroupId());
        User assignee = resolveCreateAssignee(request.getAssigneeId(), request.getGroupId());

        TaskEntity task = TaskEntity.builder()
                .projectId(project.getId())
                .groupId(group == null ? null : group.getId())
                .milestoneId(milestone == null ? null : milestone.getId())
                .parentTaskId(parent == null ? null : parent.getId())
                .assigneeId(assignee == null ? null : assignee.getId())
                .title(request.getTitle().trim())
                .description(request.getDescription())
                .deadline(request.getDueDate())
                .dueDate(request.getDueDate())
                .status(milestone == null ? TaskStatus.BACKLOG : TaskStatus.TODO)
                .priority(request.getPriority() == null ? TaskPriority.MEDIUM : request.getPriority())
                .type(request.getType() == null ? TaskType.TASK : request.getType())
                .createdBy(currentUser.getId())
                .progressPercent(0)
                .build();

        TaskEntity saved = taskRepository.save(task);
        auditLogService.log(
                currentUser,
                AuditAction.CREATE_RESEARCH_TASK,
                AuditModule.RESEARCH,
                "RESEARCH_TASK",
                saved.getId(),
                "Created official research task: " + saved.getTitle()
        );
        return TaskMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public TaskResponse patchResearchTask(Long taskId, PatchResearchTaskRequest request) {
        return taskMetadataPatchService.patch(taskId, request);
    }

    @Override
    @Transactional
    public TaskResponse patchResearchTaskStatus(Long taskId, PatchTaskStatusRequest request) {
        return taskStatusUpdateService.patch(taskId, request);
    }

    private GroupEntity resolveCreateGroup(Long groupId, ProjectEntity project) {
        if (groupId == null) {
            return null;
        }
        GroupEntity group = groupRepository.findByIdAndDeletedFalseAndActiveTrue(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Research group", groupId));
        boolean newModelMatch = group.getProject() != null && project.getId().equals(group.getProject().getId());
        boolean legacyModelMatch = project.getGroup() != null && groupId.equals(project.getGroup().getId());
        boolean conflictingProject = group.getProject() != null && !newModelMatch;
        boolean belongsToProject = !conflictingProject && (newModelMatch || legacyModelMatch);
        boolean sameLab = group.getLab() != null && group.getLab().getId() != null
                && project.getLab() != null && group.getLab().getId().equals(project.getLab().getId());
        if (!belongsToProject || !sameLab) {
            throw new IllegalArgumentException("Research group does not belong to the requested project and lab");
        }
        return group;
    }

    private MilestoneEntity resolveCreateMilestone(Long milestoneId, Long groupId, ProjectEntity project) {
        if (milestoneId == null) {
            return null;
        }
        MilestoneEntity milestone = milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(milestoneId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", milestoneId));
        if (milestone.getProject() == null || !project.getId().equals(milestone.getProject().getId())) {
            throw new IllegalArgumentException("Milestone does not belong to the requested project");
        }
        Long milestoneGroupId = milestone.getGroup() == null ? null : milestone.getGroup().getId();
        if (milestoneGroupId != null && !milestoneGroupId.equals(groupId)) {
            throw new IllegalArgumentException("Milestone group does not match the requested group");
        }
        return milestone;
    }

    private TaskEntity resolveCreateParent(Long parentTaskId, Long projectId, Long groupId) {
        if (parentTaskId == null) {
            return null;
        }
        TaskEntity parent = taskRepository.findByIdAndDeletedFalseAndActiveTrue(parentTaskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", parentTaskId));
        if (!projectId.equals(parent.getProjectId()) || !java.util.Objects.equals(groupId, parent.getGroupId())) {
            throw new IllegalArgumentException("Parent task must have the same project and group scope");
        }
        return parent;
    }

    private User resolveCreateAssignee(Long assigneeId, Long groupId) {
        if (assigneeId == null) {
            return null;
        }
        User assignee = userRepository.findById(assigneeId)
                .filter(user -> Boolean.TRUE.equals(user.getActive()))
                .filter(user -> !Boolean.TRUE.equals(user.getDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("User", assigneeId));
        if (!groupMemberRepository.existsByGroupIdAndUserIdAndActiveTrueAndDeletedFalse(groupId, assigneeId)) {
            throw new IllegalArgumentException("Assignee must have an active membership in the requested group");
        }
        return assignee;
    }

    @Override
    @Transactional
    public TaskResponse assign(Long taskId, AssignTaskRequest request) {
        TaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
        if (task.getMilestoneId() == null) {
            throw new IllegalArgumentException("Legacy task assign endpoint requires a milestone");
        }
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
        User currentUser = getCurrentUser();
        Long projectId = group.getProject() == null ? null : group.getProject().getId();
        if (currentUser.hasRole("LAB_MANAGER")) {
            assertManagerCanAccessGroup(currentUser, group);
            return taskRepository.findBoardTasksByGroupId(groupId).stream()
                    .map(task -> TaskMapper.toResponse(task, projectId))
                    .toList();
        }

        GroupRole groupRole = groupMemberRepository.findActiveRoleByGroupIdAndUserId(groupId, currentUser.getId())
                .orElseThrow(() -> new AccessDeniedException("Cannot access tasks for this group"));
        List<TaskEntity> visibleTasks = groupRole == GroupRole.LEADER
                ? taskRepository.findBoardTasksByGroupId(groupId)
                : taskRepository.findAssignedBoardTasksByGroupId(groupId, currentUser.getId());
        return visibleTasks.stream()
                .map(task -> TaskMapper.toResponse(task, projectId))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskResponse> getMyTasksInGroup(Long groupId) {
        GroupEntity group = groupRepository.findByIdAndDeletedFalseAndActiveTrue(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Research group", groupId));
        User currentUser = getCurrentUser();
        Long projectId = group.getProject() == null ? null : group.getProject().getId();

        if (!currentUser.hasRole("STUDENT") || !isMemberOfGroup(currentUser.getId(), groupId)) {
            throw new AccessDeniedException("Cannot access tasks for this group");
        }

        List<TaskEntity> tasks = taskRepository.findAssignedBoardTasksByGroupId(groupId, currentUser.getId());
        if (tasks.isEmpty()) {
            return List.of();
        }

        List<Long> taskIds = tasks.stream().map(TaskEntity::getId).toList();
        List<ReportEntity> reports = reportRepository.findActiveReportsByTaskIds(taskIds);
        Map<Long, ReportStatus> latestReportStatusMap = reports.stream()
                .collect(java.util.stream.Collectors.toMap(
                        ReportEntity::getTaskId,
                        r -> r,
                        (r1, r2) -> r1.getVersion() > r2.getVersion() ? r1 : r2
                ))
                .entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().getStatus()
                ));

        List<MilestoneEntity> milestones = milestoneRepository.findByGroupIdAndDeletedFalseAndActiveTrueOrderByDeadlineAscCreatedAtAsc(groupId);
        Map<Long, String> milestoneTitles = milestones.stream()
                .collect(java.util.stream.Collectors.toMap(MilestoneEntity::getId, MilestoneEntity::getTitle, (a, b) -> a));

        return tasks.stream()
                .map(task -> {
                    String milestoneTitle = milestoneTitles.get(task.getMilestoneId());
                    ReportStatus reportStatus = latestReportStatusMap.get(task.getId());
                    String latestReportStatusStr = reportStatus != null ? reportStatus.name() : null;
                    return TaskMapper.toResponse(task, projectId, groupId, milestoneTitle, latestReportStatusStr);
                })
                .toList();
    }

    @Override
    @Transactional
    public TaskResponse updateStatus(Long taskId, UpdateTaskStatusRequest request) {
        TaskEntity task = taskRepository.findByIdForUpdate(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
        if (task.getMilestoneId() == null) {
            throw new IllegalArgumentException("Legacy task status endpoint requires a milestone");
        }
        MilestoneEntity milestone = milestoneRepository.findByIdAndDeletedFalseAndActiveTrue(task.getMilestoneId())
                .orElseThrow(() -> new ResourceNotFoundException("Milestone", task.getMilestoneId()));
        User currentUser = getCurrentUser();

        assertCanViewTasks(currentUser, milestone);
        assertCanUpdateTaskStatus(currentUser, task);

        TaskStatus currentStatus = task.getStatus();
        TaskStatus requestedStatus = request.getStatus();

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

    private void applyStatusProgress(TaskEntity task, TaskStatus requestedStatus) {
        if (requestedStatus == TaskStatus.IN_PROGRESS
                && (task.getProgressPercent() == null || task.getProgressPercent() < 10)) {
            task.setProgressPercent(10);
        }
        if (requestedStatus == TaskStatus.IN_REVIEW) {
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

    private User getUsableCurrentUser() {
        User user = getCurrentUser();
        if (!Boolean.TRUE.equals(user.getActive()) || Boolean.TRUE.equals(user.getDeleted())) {
            throw new AccessDeniedException("Authenticated user is inactive or deleted");
        }
        return user;
    }

    private void assertManagerCanAccessGroup(User currentUser, GroupEntity group) {
        Laboratory managedLab = laboratoryRepository.findFirstByManagerIdAndDeletedFalse(currentUser.getId())
                .orElseThrow(() -> new AccessDeniedException("Lab manager is not assigned to any lab"));
        if (group.getLab() == null || !managedLab.getId().equals(group.getLab().getId())) {
            throw new AccessDeniedException("Cannot access tasks from another lab");
        }
    }

    private boolean isMemberOfGroup(Long userId, Long groupId) {
        return groupMemberRepository.existsByGroupIdAndUserIdAndActiveTrueAndDeletedFalse(groupId, userId);
    }

    @SuppressWarnings("unused")
    private boolean isLeaderOfGroup(Long userId, Long groupId) {
        return groupMemberRepository.findActiveRoleByGroupIdAndUserId(groupId, userId)
                .filter(role -> role == GroupRole.LEADER)
                .isPresent();
    }

    private SystemConfigResponse systemConfig() {
        return systemConfigService.getConfig();
    }
}
