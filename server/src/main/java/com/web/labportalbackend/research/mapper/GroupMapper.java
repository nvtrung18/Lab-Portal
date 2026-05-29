package com.web.labportalbackend.research.mapper;

import com.web.labportalbackend.research.dto.response.GroupMemberResponse;
import com.web.labportalbackend.research.dto.response.GroupResponse;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.GroupMemberEntity;
import com.web.labportalbackend.research.enums.GroupRole;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Comparator;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GroupMapper {

    public static GroupResponse toResponse(GroupEntity group) {
        return toResponse(group, null);
    }

    public static GroupResponse toResponse(GroupEntity group, Long projectCount) {
        return toResponse(group, projectCount, null);
    }

    public static GroupResponse toResponse(GroupEntity group, Long projectCount, Long currentUserId) {
        List<GroupMemberEntity> activeMembers = group.getMembers().stream()
                .filter(member -> !Boolean.FALSE.equals(member.getActive()) && !Boolean.TRUE.equals(member.getDeleted()))
                .toList();
        GroupRole myRole = activeMembers.stream()
                .filter(member -> currentUserId != null && currentUserId.equals(member.getUser().getId()))
                .map(GroupMemberEntity::getRole)
                .findFirst()
                .orElse(null);
        return GroupResponse.builder()
                .id(group.getId())
                .labId(group.getLab().getId())
                .topicId(group.getTopic() != null ? group.getTopic().getId() : null)
                .projectId(group.getProject() != null ? group.getProject().getId() : null)
                .topicName(group.getTopic() != null ? group.getTopic().getName() : null)
                .projectTitle(group.getProject() != null ? group.getProject().getTitle() : null)
                .projectCode(group.getProject() != null ? group.getProject().getCode() : null)
                .leaderName(toDisplayName(group.getLeader()))
                .managerName(resolveManagerName(group))
                .name(group.getName())
                .description(group.getDescription())
                .objective(group.getObjective())
                .plan(group.getPlan())
                .status(group.getStatus())
                .leaderId(group.getLeader().getId())
                .myRole(myRole)
                .myGroupRole(myRole)
                .createdByName(toDisplayName(group.getLeader()))
                .memberCount(activeMembers.size())
                .projectCount(projectCount)
                .createdAt(group.getCreatedAt())
                .updatedAt(group.getUpdatedAt())
                .members(activeMembers.stream()
                        .sorted(Comparator.comparing(GroupMemberEntity::getJoinedAt))
                        .map(GroupMapper::toMemberResponse)
                        .toList())
                .build();
    }

    public static GroupMemberResponse toMemberResponse(GroupMemberEntity member) {
        return GroupMemberResponse.builder()
                .id(member.getId())
                .groupId(member.getGroup().getId())
                .userId(member.getUser().getId())
                .fullName(member.getUser().getFullName())
                .email(member.getUser().getEmail())
                .role(member.getRole())
                .joinedAt(member.getJoinedAt())
                .build();
    }

    private static String resolveManagerName(GroupEntity group) {
        if (group.getProject() != null && group.getProject().getManager() != null) {
            return toDisplayName(group.getProject().getManager());
        }
        if (group.getTopic() != null && group.getTopic().getManager() != null) {
            return toDisplayName(group.getTopic().getManager());
        }
        if (group.getLab().getManager() != null) {
            return toDisplayName(group.getLab().getManager());
        }
        return null;
    }

    private static String toDisplayName(com.web.labportalbackend.auth.entity.User user) {
        if (user == null) {
            return null;
        }
        return user.getFullName() != null ? user.getFullName() : user.getEmail();
    }
}
