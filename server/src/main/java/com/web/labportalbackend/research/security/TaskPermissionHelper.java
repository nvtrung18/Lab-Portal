package com.web.labportalbackend.research.security;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.GroupMemberEntity;
import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.entity.TaskEntity;
import com.web.labportalbackend.research.enums.GroupRole;
import com.web.labportalbackend.research.repository.GroupMemberRepository;
import com.web.labportalbackend.research.repository.GroupRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TaskPermissionHelper {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final LaboratoryRepository laboratoryRepository;

    public boolean canCreateOfficialTask(Long userId, ProjectEntity project) {
        if (!hasUsableUser(userId) || !isLabManager(userId) || project == null || project.getId() == null
                || !Boolean.TRUE.equals(project.getActive()) || Boolean.TRUE.equals(project.getDeleted())
                || project.getLab() == null || project.getLab().getId() == null
                || !Boolean.TRUE.equals(project.getLab().getActive())
                || Boolean.TRUE.equals(project.getLab().getDeleted())) {
            return false;
        }
        return laboratoryRepository.existsByIdAndManagerIdAndActiveTrueAndDeletedFalse(
                project.getLab().getId(), userId);
    }

    public boolean canViewProjectBoard(Long userId, ProjectEntity project) {
        if (!hasUsableUser(userId) || project == null || project.getId() == null
                || project.getLab() == null || project.getLab().getId() == null
                || !Boolean.TRUE.equals(project.getLab().getActive())
                || Boolean.TRUE.equals(project.getLab().getDeleted())
                || hasInconsistentProjectGroup(project)) {
            return false;
        }
        if (isLabManager(userId)) {
            return laboratoryRepository.findFirstByManagerIdAndDeletedFalse(userId)
                    .filter(lab -> Boolean.TRUE.equals(lab.getActive()))
                    .map(Laboratory::getId)
                    .filter(project.getLab().getId()::equals)
                    .isPresent();
        }
        return !groupMemberRepository.findActiveGroupIdsByProjectIdAndUserIdAndRole(
                        project.getId(), userId, GroupRole.LEADER).isEmpty()
                || !groupMemberRepository.findActiveGroupIdsByProjectIdAndUserIdAndRole(
                        project.getId(), userId, GroupRole.MEMBER).isEmpty();
    }

    /**
     * AI-context-specific project visibility. The manager branch checks ownership
     * of this exact active laboratory and never relies on a first-lab lookup.
     */
    public boolean canViewProjectContext(Long userId, ProjectEntity project) {
        if (!hasUsableUser(userId) || project == null || project.getId() == null
                || !Boolean.TRUE.equals(project.getActive()) || Boolean.TRUE.equals(project.getDeleted())
                || project.getLab() == null || project.getLab().getId() == null
                || !Boolean.TRUE.equals(project.getLab().getActive())
                || Boolean.TRUE.equals(project.getLab().getDeleted())
                || hasInconsistentProjectGroup(project)) {
            return false;
        }
        if (isLabManager(userId)) {
            return laboratoryRepository.existsByIdAndManagerIdAndActiveTrueAndDeletedFalse(
                    project.getLab().getId(), userId);
        }
        return !groupMemberRepository.findActiveGroupIdsByProjectIdAndUserIdAndRole(
                        project.getId(), userId, GroupRole.LEADER).isEmpty()
                || !groupMemberRepository.findActiveGroupIdsByProjectIdAndUserIdAndRole(
                        project.getId(), userId, GroupRole.MEMBER).isEmpty();
    }

    private boolean hasInconsistentProjectGroup(ProjectEntity project) {
        GroupEntity group = project.getGroup();
        if (group == null) {
            return false;
        }
        return group.getId() == null || !Boolean.TRUE.equals(group.getActive()) || Boolean.TRUE.equals(group.getDeleted())
                || group.getLab() == null || group.getLab().getId() == null
                || !project.getLab().getId().equals(group.getLab().getId())
                || (group.getProject() != null && !project.getId().equals(group.getProject().getId()));
    }

    public boolean canViewTask(Long userId, TaskEntity task) {
        if (!hasUsableUser(userId) || !hasTaskScope(task)) {
            return false;
        }
        return isManagerOfTaskProjectOrLab(userId, task)
                || isLeaderInTaskGroup(userId, task)
                || isMemberInTaskGroup(userId, task)
                || isScopedTaskAssignee(userId, task);
    }

    public boolean canManageTask(Long userId, TaskEntity task) {
        if (!hasUsableUser(userId) || !hasTaskScope(task)) {
            return false;
        }
        return isManagerOfTaskProjectOrLab(userId, task)
                || isLeaderInTaskGroup(userId, task);
    }

    public boolean canUpdateTaskMetadata(Long userId, TaskEntity task) {
        return canManageTask(userId, task);
    }

    public boolean canUpdateTaskStatus(Long userId, TaskEntity task) {
        if (!hasUsableUser(userId) || !hasTaskScope(task)) {
            return false;
        }
        return isManagerOfTaskProjectOrLab(userId, task)
                || isLeaderInTaskGroup(userId, task)
                || isScopedTaskAssignee(userId, task);
    }

    public boolean isTaskAssignee(Long userId, TaskEntity task) {
        return userId != null
                && task != null
                && task.getAssigneeId() != null
                && userId.equals(task.getAssigneeId());
    }

    public boolean isLeaderInTaskGroup(Long userId, TaskEntity task) {
        return taskGroupRole(userId, task) == GroupRole.LEADER;
    }

    public boolean isMemberInTaskGroup(Long userId, TaskEntity task) {
        GroupRole role = taskGroupRole(userId, task);
        return role == GroupRole.LEADER || role == GroupRole.MEMBER;
    }

    public boolean isManagerOfTaskProjectOrLab(Long userId, TaskEntity task) {
        if (userId == null || task == null || !isLabManager(userId)) {
            return false;
        }

        Long projectLabId = null;
        boolean invalidScope = false;
        if (task.getProjectId() != null) {
            ProjectEntity project = projectRepository.findByIdAndDeletedFalseAndActiveTrue(task.getProjectId())
                    .orElse(null);
            if (project == null || project.getLab() == null || project.getLab().getId() == null) {
                invalidScope = true;
            } else {
                projectLabId = project.getLab().getId();
            }
        }

        Long groupLabId = null;
        if (task.getGroupId() != null) {
            GroupEntity group = groupRepository.findByIdAndDeletedFalseAndActiveTrue(task.getGroupId())
                    .orElse(null);
            if (group == null || group.getLab() == null || group.getLab().getId() == null) {
                invalidScope = true;
            } else {
                groupLabId = group.getLab().getId();
            }
        }

        if (invalidScope || (task.getProjectId() == null && task.getGroupId() == null)) {
            return false;
        }
        if (projectLabId != null && groupLabId != null && !projectLabId.equals(groupLabId)) {
            return false;
        }
        Long targetLabId = projectLabId != null ? projectLabId : groupLabId;
        return targetLabId != null
                && laboratoryRepository.existsByIdAndManagerIdAndActiveTrueAndDeletedFalse(targetLabId, userId);
    }

    /**
     * Resolve current task scope in the fixed project -> group -> laboratory
     * lock order. The caller already owns task and actor locks and calls the
     * membership method only after this method returns.
     */
    public StatusAuthorizationScope resolveStatusAuthorizationScope(TaskEntity task) {
        if (task == null || (task.getProjectId() == null && task.getGroupId() == null)) {
            return StatusAuthorizationScope.invalid();
        }

        Long projectLabId = null;
        boolean invalidScope = false;
        if (task.getProjectId() != null) {
            ProjectEntity project = projectRepository.findByIdForStatusAuthorization(task.getProjectId())
                    .orElse(null);
            if (project == null || project.getLab() == null || project.getLab().getId() == null) {
                invalidScope = true;
            } else {
                projectLabId = project.getLab().getId();
            }
        }

        Long groupLabId = null;
        if (task.getGroupId() != null) {
            GroupEntity group = groupRepository.findByIdForStatusAuthorization(task.getGroupId())
                    .orElse(null);
            if (group == null || group.getLab() == null || group.getLab().getId() == null) {
                invalidScope = true;
            } else {
                groupLabId = group.getLab().getId();
            }
        }

        if (invalidScope
                || (projectLabId != null && groupLabId != null && !projectLabId.equals(groupLabId))) {
            return StatusAuthorizationScope.invalid();
        }
        Long targetLabId = projectLabId != null ? projectLabId : groupLabId;
        Laboratory laboratory = laboratoryRepository.findByIdForStatusAuthorization(targetLabId)
                .orElse(null);
        if (laboratory == null) {
            return StatusAuthorizationScope.invalid();
        }
        return new StatusAuthorizationScope(true, targetLabId);
    }

    public boolean isLaboratoryManagedByForStatusAuthorization(Long laboratoryId, Long userId) {
        return laboratoryId != null
                && userId != null
                && laboratoryRepository
                        .findManagedByIdForStatusAuthorization(laboratoryId, userId)
                        .isPresent();
    }

    public GroupRole findGroupRoleForStatusAuthorization(Long userId, TaskEntity task) {
        if (userId == null || task == null || task.getGroupId() == null) {
            return null;
        }
        return groupMemberRepository.findActiveForStatusAuthorization(task.getGroupId(), userId)
                .map(GroupMemberEntity::getRole)
                .orElse(null);
    }

    public record StatusAuthorizationScope(boolean valid, Long laboratoryId) {
        private static StatusAuthorizationScope invalid() {
            return new StatusAuthorizationScope(false, null);
        }
    }

    private boolean isScopedTaskAssignee(Long userId, TaskEntity task) {
        return task != null && task.getGroupId() != null && isTaskAssignee(userId, task);
    }

    private GroupRole taskGroupRole(Long userId, TaskEntity task) {
        if (userId == null || task == null || task.getGroupId() == null) {
            return null;
        }
        return groupMemberRepository.findActiveRoleByGroupIdAndUserId(task.getGroupId(), userId)
                .orElse(null);
    }

    private boolean isLabManager(Long userId) {
        return userRepository.findById(userId)
                .filter(user -> user.hasRole("LAB_MANAGER"))
                .isPresent();
    }

    private boolean hasUsableUser(Long userId) {
        return userId != null
                && userRepository.findById(userId)
                        .filter(user -> Boolean.TRUE.equals(user.getActive()))
                        .filter(user -> !Boolean.TRUE.equals(user.getDeleted()))
                        .isPresent();
    }

    private boolean hasTaskScope(TaskEntity task) {
        return task != null && (task.getProjectId() != null || task.getGroupId() != null);
    }
}
