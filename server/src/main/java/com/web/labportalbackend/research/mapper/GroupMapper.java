package com.web.labportalbackend.research.mapper;

import com.web.labportalbackend.research.dto.response.GroupMemberResponse;
import com.web.labportalbackend.research.dto.response.GroupResponse;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.entity.GroupMemberEntity;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Comparator;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GroupMapper {

    public static GroupResponse toResponse(GroupEntity group) {
        return toResponse(group, null);
    }

    public static GroupResponse toResponse(GroupEntity group, Long projectCount) {
        return GroupResponse.builder()
                .id(group.getId())
                .labId(group.getLab().getId())
                .topicId(group.getTopic() != null ? group.getTopic().getId() : null)
                .name(group.getName())
                .description(group.getDescription())
                .objective(group.getObjective())
                .plan(group.getPlan())
                .status(group.getStatus())
                .leaderId(group.getLeader().getId())
                .createdByName(group.getLeader().getFullName() != null
                        ? group.getLeader().getFullName()
                        : group.getLeader().getEmail())
                .memberCount(group.getMembers().size())
                .projectCount(projectCount)
                .createdAt(group.getCreatedAt())
                .updatedAt(group.getUpdatedAt())
                .members(group.getMembers().stream()
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
                .role(member.getRole())
                .joinedAt(member.getJoinedAt())
                .build();
    }
}
