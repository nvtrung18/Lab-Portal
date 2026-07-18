package com.web.labportalbackend.research.security;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.research.entity.GroupEntity;
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

        Long managedLabId = laboratoryRepository.findFirstByManagerIdAndDeletedFalse(userId)
                .map(Laboratory::getId)
                .orElse(null);
        if (managedLabId == null) {
            return false;
        }

        Long projectLabId = task.getProjectId() == null
                ? null
                : projectRepository.findByIdAndDeletedFalseAndActiveTrue(task.getProjectId())
                        .map(ProjectEntity::getLab)
                        .map(Laboratory::getId)
                        .orElse(null);
        Long groupLabId = task.getGroupId() == null
                ? null
                : groupRepository.findByIdAndDeletedFalseAndActiveTrue(task.getGroupId())
                        .map(GroupEntity::getLab)
                        .map(Laboratory::getId)
                        .orElse(null);

        if (projectLabId == null && groupLabId == null) {
            return false;
        }
        if (projectLabId != null && !managedLabId.equals(projectLabId)) {
            return false;
        }
        return groupLabId == null || managedLabId.equals(groupLabId);
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
