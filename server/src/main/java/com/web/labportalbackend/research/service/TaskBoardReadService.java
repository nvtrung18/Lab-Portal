package com.web.labportalbackend.research.service;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.research.dto.response.ProjectTaskBoardResponse;
import com.web.labportalbackend.research.dto.response.TaskBacklogPageResponse;
import com.web.labportalbackend.research.dto.response.TaskBoardColumnResponse;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.entity.TaskEntity;
import com.web.labportalbackend.research.enums.GroupRole;
import com.web.labportalbackend.research.enums.TaskPriority;
import com.web.labportalbackend.research.enums.TaskStatus;
import com.web.labportalbackend.research.enums.TaskType;
import com.web.labportalbackend.research.mapper.TaskMapper;
import com.web.labportalbackend.research.repository.GroupMemberRepository;
import com.web.labportalbackend.research.repository.GroupRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.repository.TaskRepository;
import com.web.labportalbackend.research.security.TaskPermissionHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TaskBoardReadService {
    private static final List<TaskStatus> DEFAULT_COLUMNS = List.of(
            TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.IN_REVIEW,
            TaskStatus.NEEDS_REVISION, TaskStatus.DONE, TaskStatus.BLOCKED);
    private static final List<Long> NO_GROUPS = List.of(-1L);

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final TaskPermissionHelper permissionHelper;

    @Transactional(readOnly = true)
    public ProjectTaskBoardResponse read(Long projectId, Long groupId, Long assigneeId, TaskStatus status,
                                         TaskPriority priority, TaskType type,
                                         boolean includeBacklog, boolean includeCancelled) {
        ProjectEntity project = projectRepository.findByIdAndDeletedFalseAndActiveTrue(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
        User user = currentUser();
        if (!permissionHelper.canViewProjectBoard(user.getId(), project)) {
            throw new AccessDeniedException("Cannot access tasks for this project");
        }

        boolean manager = user.hasRole("LAB_MANAGER");
        List<Long> leaderGroups = manager ? List.of() : groupIds(projectId, user.getId(), GroupRole.LEADER);
        List<Long> memberGroups = manager ? List.of() : groupIds(projectId, user.getId(), GroupRole.MEMBER);
        validateGroupFilter(project, groupId, manager, leaderGroups, memberGroups);
        validateAssigneeFilter(projectId, assigneeId, user, manager, leaderGroups);

        boolean queryBacklog = status != null ? status == TaskStatus.BACKLOG : includeBacklog;
        boolean queryCancelled = status != null ? status == TaskStatus.CANCELLED : includeCancelled;
        List<TaskEntity> tasks = manager
                ? taskRepository.findBoardTasksForManager(projectId, groupId, assigneeId, status, priority, type,
                        queryBacklog, queryCancelled)
                : taskRepository.findBoardTasksForStudent(projectId, nonEmpty(leaderGroups), nonEmpty(memberGroups),
                        user.getId(), groupId, assigneeId, status, priority, type, queryBacklog, queryCancelled);

        List<TaskStatus> columns = columns(status, includeBacklog, includeCancelled);
        Map<TaskStatus, List<com.web.labportalbackend.research.dto.response.TaskResponse>> grouped =
                new EnumMap<>(TaskStatus.class);
        columns.forEach(column -> grouped.put(column, new ArrayList<>()));
        Set<Long> mappedTaskIds = new HashSet<>();
        tasks.stream()
                .filter(task -> mappedTaskIds.add(task.getId()))
                .forEach(task -> grouped.get(task.getStatus()).add(TaskMapper.toResponse(task)));

        return ProjectTaskBoardResponse.builder()
                .projectId(projectId)
                .columns(columns.stream()
                        .map(column -> TaskBoardColumnResponse.builder().status(column).tasks(List.copyOf(grouped.get(column))).build())
                        .toList())
                .build();
    }

    @Transactional(readOnly = true)
    public TaskBacklogPageResponse readBacklog(Long projectId, int page, int size) {
        ProjectEntity project = projectRepository.findByIdAndDeletedFalseAndActiveTrue(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
        User user = currentUser();
        if (!permissionHelper.canViewProjectBoard(user.getId(), project)) {
            throw new AccessDeniedException("Cannot access tasks for this project");
        }

        boolean manager = user.hasRole("LAB_MANAGER");
        List<Long> leaderGroups = manager ? List.of() : groupIds(projectId, user.getId(), GroupRole.LEADER);
        List<Long> memberGroups = manager ? List.of() : groupIds(projectId, user.getId(), GroupRole.MEMBER);
        validatePagination(page, size);

        PageRequest pageRequest = PageRequest.of(page, size);
        Page<TaskEntity> tasks = manager
                ? taskRepository.findBacklogTasksForManager(projectId, pageRequest)
                : taskRepository.findBacklogTasksForStudent(projectId, nonEmpty(leaderGroups), nonEmpty(memberGroups),
                        user.getId(), pageRequest);

        return new TaskBacklogPageResponse(
                tasks.getContent().stream().map(TaskMapper::toResponse).toList(),
                tasks.getNumber(),
                tasks.getSize(),
                tasks.getTotalElements(),
                tasks.getTotalPages()
        );
    }

    private void validatePagination(int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("Page must be zero or greater");
        }
        if (size <= 0 || size > 100) {
            throw new IllegalArgumentException("Size must be between 1 and 100");
        }
    }

    private void validateGroupFilter(ProjectEntity project, Long groupId, boolean manager,
                                     List<Long> leaderGroups, List<Long> memberGroups) {
        if (groupId == null) return;
        GroupEntity group = groupRepository.findByIdAndDeletedFalseAndActiveTrue(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Research group", groupId));
        boolean belongsToProject = (group.getProject() != null && project.getId().equals(group.getProject().getId()))
                || (project.getGroup() != null && groupId.equals(project.getGroup().getId()));
        boolean sameLab = group.getLab() != null && project.getLab() != null
                && group.getLab().getId() != null && group.getLab().getId().equals(project.getLab().getId());
        if (!belongsToProject || !sameLab
                || (!manager && !leaderGroups.contains(groupId) && !memberGroups.contains(groupId))) {
            throw new AccessDeniedException("Research group is outside the requested project or task scope");
        }
    }

    private void validateAssigneeFilter(Long projectId, Long assigneeId, User user, boolean manager,
                                        List<Long> leaderGroups) {
        if (assigneeId == null) return;
        userRepository.findById(assigneeId)
                .filter(candidate -> Boolean.TRUE.equals(candidate.getActive()))
                .filter(candidate -> !Boolean.TRUE.equals(candidate.getDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("User", assigneeId));
        List<Long> assigneeGroups = new ArrayList<>(groupIds(projectId, assigneeId, GroupRole.LEADER));
        assigneeGroups.addAll(groupIds(projectId, assigneeId, GroupRole.MEMBER));
        boolean allowed = !assigneeGroups.isEmpty()
                && (manager || assigneeId.equals(user.getId()) || assigneeGroups.stream().anyMatch(leaderGroups::contains));
        if (!allowed) {
            throw new AccessDeniedException("Assignee is outside the requested project or task scope");
        }
    }

    private List<Long> groupIds(Long projectId, Long userId, GroupRole role) {
        return groupMemberRepository.findActiveGroupIdsByProjectIdAndUserIdAndRole(projectId, userId, role);
    }

    private List<Long> nonEmpty(List<Long> groupIds) {
        return groupIds.isEmpty() ? NO_GROUPS : groupIds;
    }

    private List<TaskStatus> columns(TaskStatus status, boolean includeBacklog, boolean includeCancelled) {
        if (status != null) return List.of(status);
        List<TaskStatus> columns = new ArrayList<>();
        if (includeBacklog) columns.add(TaskStatus.BACKLOG);
        columns.addAll(DEFAULT_COLUMNS);
        if (includeCancelled) columns.add(TaskStatus.CANCELLED);
        return List.copyOf(columns);
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication is required");
        }
        return userRepository.findByUsername(authentication.getName())
                .filter(user -> Boolean.TRUE.equals(user.getActive()))
                .filter(user -> !Boolean.TRUE.equals(user.getDeleted()))
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", authentication.getName()));
    }
}
