package com.web.labportalbackend.research.mapper;

import com.web.labportalbackend.research.dto.response.LogResponse;
import com.web.labportalbackend.research.entity.LogEntity;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LogMapper {

    public static LogResponse toResponse(LogEntity log) {
        return LogResponse.builder()
                .id(log.getId())
                .projectId(log.getProjectId())
                .userId(log.getUserId())
                .action(log.getAction())
                .details(log.getDetails())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
