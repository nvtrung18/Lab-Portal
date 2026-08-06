package com.web.labportalbackend.ai.service.impl;

import com.web.labportalbackend.ai.service.AiContextService;
import com.web.labportalbackend.ai.service.AiResearchContext;
import com.web.labportalbackend.ai.service.AiResearchContextRequest;
import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.MilestoneEntity;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.entity.TaskEntity;
import com.web.labportalbackend.research.enums.GroupRole;
import com.web.labportalbackend.research.repository.GroupMemberRepository;
import com.web.labportalbackend.research.repository.GroupRepository;
import com.web.labportalbackend.research.repository.MilestoneRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.repository.TaskRepository;
import com.web.labportalbackend.research.security.TaskPermissionHelper;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiContextServiceImpl implements AiContextService {

    private static final List<Long> NO_GROUPS = List.of(-1L);

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final MilestoneRepository milestoneRepository;
    private final TaskRepository taskRepository;
    private final TaskPermissionHelper permissionHelper;

    public AiContextServiceImpl(UserRepository userRepository,
                                ProjectRepository projectRepository,
                                GroupRepository groupRepository,
                                GroupMemberRepository groupMemberRepository,
                                MilestoneRepository milestoneRepository,
                                TaskRepository taskRepository,
                                TaskPermissionHelper permissionHelper) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.milestoneRepository = milestoneRepository;
        this.taskRepository = taskRepository;
        this.permissionHelper = permissionHelper;
    }

    @Override
    @Transactional(readOnly = true)
    public AiResearchContext buildResearchContext(AiResearchContextRequest request) {
        User user = currentUser();
        List<String> roles = normalizedRoles(user);
        boolean manager = roles.contains("LAB_MANAGER");
        if (!manager && !roles.contains("STUDENT")) {
            throw new AccessDeniedException("Research context is not available for this role");
        }

        ProjectEntity project = projectRepository.findByIdAndDeletedFalseAndActiveTrue(request.projectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project", request.projectId()));
        if (!permissionHelper.canViewProjectContext(user.getId(), project)) {
            throw new AccessDeniedException("Cannot access AI research context for this project");
        }

        GroupEntity requestedGroup = request.groupId() == null ? null : groupRepository
                .findByIdAndDeletedFalseAndActiveTrue(request.groupId())
                .orElseThrow(() -> new ResourceNotFoundException("Research group", request.groupId()));
        if (requestedGroup != null && !isCoherentGroup(project, requestedGroup)) {
            throw new AccessDeniedException("Research group is outside the requested project scope");
        }

        Scope scope = scopeFor(user.getId(), project.getId(), manager);
        if (requestedGroup != null && !scope.canAccess(requestedGroup.getId())) {
            throw new AccessDeniedException("Research group is outside the requested project scope");
        }

        List<GroupEntity> groups = requestedGroup == null
                ? projectGroups(project, scope)
                : List.of(requestedGroup);
        List<TaskEntity> tasks = authorizedTasks(project.getId(), requestedGroup == null ? null : requestedGroup.getId(),
                user.getId(), scope);
        List<MilestoneEntity> milestones = authorizedMilestones(project, requestedGroup, scope, user.getId(), tasks);

        return new AiResearchContext(
                new AiResearchContext.Identity(user.getId(), roles),
                new AiResearchContext.Laboratory(project.getLab().getId(), project.getLab().getLabName()),
                new AiResearchContext.Project(project.getId(), project.getCode(), project.getTitle(), project.getStatus(),
                        project.getStartDate(), project.getEndDate()),
                groups.stream().map(group -> new AiResearchContext.Group(group.getId(), group.getName(),
                        scope.roleFor(group.getId()))).toList(),
                milestones.stream().map(this::toMilestone).toList(),
                tasks.stream().map(this::toTask).toList());
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication.getName() == null
                || authentication.getName().isBlank()) {
            throw new AccessDeniedException("Authentication is required");
        }
        return userRepository.findByUsername(authentication.getName())
                .filter(user -> Boolean.TRUE.equals(user.getActive()))
                .filter(user -> !Boolean.TRUE.equals(user.getDeleted()))
                .orElseThrow(() -> new AccessDeniedException("Authenticated user is not active"));
    }

    private List<String> normalizedRoles(User user) {
        return user.getRoles().stream()
                .map(Role::getName)
                .filter(name -> name != null && !name.isBlank())
                .map(name -> name.trim().toUpperCase(Locale.ROOT))
                .map(name -> name.startsWith("ROLE_") ? name.substring("ROLE_".length()) : name)
                .distinct()
                .sorted()
                .toList();
    }

    private Scope scopeFor(Long userId, Long projectId, boolean manager) {
        if (manager) {
            return Scope.manager();
        }
        List<Long> leaderGroups = groupMemberRepository
                .findActiveGroupIdsByProjectIdAndUserIdAndRole(projectId, userId, GroupRole.LEADER);
        List<Long> memberGroups = groupMemberRepository
                .findActiveGroupIdsByProjectIdAndUserIdAndRole(projectId, userId, GroupRole.MEMBER);
        return Scope.student(leaderGroups, memberGroups);
    }

    private List<GroupEntity> projectGroups(ProjectEntity project, Scope scope) {
        Map<Long, GroupEntity> candidates = new LinkedHashMap<>();
        groupRepository.findByProjectIdAndDeletedFalseAndActiveTrue(project.getId())
                .forEach(group -> candidates.put(group.getId(), group));
        if (project.getGroup() != null && project.getGroup().getId() != null) {
            candidates.putIfAbsent(project.getGroup().getId(), project.getGroup());
        }
        return candidates.values().stream()
                .filter(group -> isCoherentGroup(project, group))
                .filter(group -> scope.canAccess(group.getId()))
                .sorted(Comparator.comparing(GroupEntity::getId))
                .toList();
    }

    private List<TaskEntity> authorizedTasks(Long projectId, Long groupId, Long userId, Scope scope) {
        List<TaskEntity> selected = scope.manager
                ? taskRepository.findBoardTasksForManager(projectId, groupId, null, null, null, null, true, true)
                : taskRepository.findBoardTasksForStudent(projectId, nonEmpty(scope.leaderGroupIds),
                        nonEmpty(scope.memberGroupIds), userId, groupId, null, null, null, null, true, true);
        return selected.stream()
                .filter(task -> permissionHelper.canViewTask(userId, task))
                .toList();
    }

    private List<MilestoneEntity> authorizedMilestones(ProjectEntity project, GroupEntity requestedGroup, Scope scope,
                                                        Long userId, List<TaskEntity> tasks) {
        List<MilestoneEntity> candidates = requestedGroup == null
                ? milestoneRepository.findByProjectIdAndDeletedFalseAndActiveTrueOrderByDeadlineAscCreatedAtAsc(project.getId())
                : milestoneRepository.findByGroupIdAndDeletedFalseAndActiveTrueOrderByDeadlineAscCreatedAtAsc(
                        requestedGroup.getId());
        Set<Long> visibleTaskMilestoneIds = tasks.stream().map(TaskEntity::getMilestoneId)
                .filter(id -> id != null).collect(Collectors.toSet());
        return candidates.stream()
                .filter(milestone -> milestone.getProject() != null
                        && project.getId().equals(milestone.getProject().getId()))
                .filter(milestone -> milestone.getGroup() == null
                        || isCoherentGroup(project, milestone.getGroup()))
                .filter(milestone -> isVisibleMilestone(milestone, requestedGroup, scope, userId, visibleTaskMilestoneIds))
                .toList();
    }

    private boolean isVisibleMilestone(MilestoneEntity milestone, GroupEntity requestedGroup, Scope scope,
                                       Long userId, Set<Long> visibleTaskMilestoneIds) {
        Long groupId = milestone.getGroup() == null ? null : milestone.getGroup().getId();
        if (requestedGroup != null && !requestedGroup.getId().equals(groupId)) {
            return false;
        }
        if (scope.manager) {
            return groupId == null || scope.canAccess(groupId);
        }
        if (groupId == null || !scope.canAccess(groupId)) {
            return false;
        }
        if (scope.roleFor(groupId) == GroupRole.LEADER) {
            return true;
        }
        return (milestone.getAssignedToStudent() != null
                && userId.equals(milestone.getAssignedToStudent().getId()))
                || visibleTaskMilestoneIds.contains(milestone.getId());
    }

    private boolean isCoherentGroup(ProjectEntity project, GroupEntity group) {
        if (group == null || group.getId() == null || !Boolean.TRUE.equals(group.getActive())
                || Boolean.TRUE.equals(group.getDeleted()) || group.getLab() == null || group.getLab().getId() == null
                || !Boolean.TRUE.equals(group.getLab().getActive()) || Boolean.TRUE.equals(group.getLab().getDeleted())
                || project.getLab() == null || project.getLab().getId() == null
                || !project.getLab().getId().equals(group.getLab().getId())) {
            return false;
        }
        return group.getProject() != null && project.getId().equals(group.getProject().getId())
                || project.getGroup() != null && group.getId().equals(project.getGroup().getId());
    }

    private List<Long> nonEmpty(List<Long> groupIds) {
        return groupIds.isEmpty() ? NO_GROUPS : groupIds;
    }

    private AiResearchContext.Milestone toMilestone(MilestoneEntity milestone) {
        return new AiResearchContext.Milestone(milestone.getId(), milestone.getTitle(), milestone.getName(),
                milestone.getStatus(), milestone.getStartDate(), milestone.getEndDate(), milestone.getDeadline(),
                milestone.getProgressPercent());
    }

    private AiResearchContext.Task toTask(TaskEntity task) {
        return new AiResearchContext.Task(task.getId(), task.getTitle(), task.getStatus(), task.getPriority(),
                task.getType(), task.getDueDate(), task.getDeadline(), task.getProgressPercent());
    }

    private static final class Scope {
        private final boolean manager;
        private final List<Long> leaderGroupIds;
        private final List<Long> memberGroupIds;

        private Scope(boolean manager, List<Long> leaderGroupIds, List<Long> memberGroupIds) {
            this.manager = manager;
            this.leaderGroupIds = List.copyOf(leaderGroupIds);
            this.memberGroupIds = List.copyOf(memberGroupIds);
        }

        static Scope manager() {
            return new Scope(true, List.of(), List.of());
        }

        static Scope student(List<Long> leaderGroupIds, List<Long> memberGroupIds) {
            return new Scope(false, leaderGroupIds, memberGroupIds);
        }

        boolean canAccess(Long groupId) {
            return manager || leaderGroupIds.contains(groupId) || memberGroupIds.contains(groupId);
        }

        GroupRole roleFor(Long groupId) {
            if (leaderGroupIds.contains(groupId)) {
                return GroupRole.LEADER;
            }
            if (memberGroupIds.contains(groupId)) {
                return GroupRole.MEMBER;
            }
            return null;
        }
    }
}
