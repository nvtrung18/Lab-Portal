package com.web.labportalbackend.research.mapper;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.research.dto.response.TopicResponse;
import com.web.labportalbackend.research.entity.ResearchTopicEntity;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TopicMapper {

    public static TopicResponse toResponse(ResearchTopicEntity topic, Long groupCount) {
        return TopicResponse.builder()
                .id(topic.getId())
                .labId(topic.getLab().getId())
                .name(topic.getName())
                .description(topic.getDescription())
                .requirements(topic.getRequirements())
                .references(topic.getReferences())
                .managerName(displayName(topic.getManager()))
                .createdByName(displayName(topic.getCreatedBy()))
                .status(topic.getStatus())
                .groupCount(groupCount)
                .createdAt(topic.getCreatedAt())
                .updatedAt(topic.getUpdatedAt())
                .build();
    }

    private static String displayName(User user) {
        if (user == null) {
            return null;
        }
        return user.getFullName() != null ? user.getFullName() : user.getEmail();
    }
}
