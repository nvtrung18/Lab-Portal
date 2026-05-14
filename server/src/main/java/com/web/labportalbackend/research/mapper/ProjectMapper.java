package com.web.labportalbackend.research.mapper;

import com.web.labportalbackend.research.dto.response.ProjectDetailResponse;
import com.web.labportalbackend.research.dto.response.ProjectResponse;
import com.web.labportalbackend.research.entity.ProjectEntity;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProjectMapper {

    public static ProjectResponse toResponse(ProjectEntity project) {
        return ProjectResponse.builder()
                .id(project.getId())
                .groupId(project.getGroup().getId())
                .title(project.getTitle())
                .status(project.getStatus())
                .startDate(project.getStartDate())
                .endDate(project.getEndDate())
                .build();
    }

    public static ProjectDetailResponse toDetailResponse(ProjectEntity project) {
        return ProjectDetailResponse.builder()
                .id(project.getId())
                .groupId(project.getGroup().getId())
                .title(project.getTitle())
                .description(project.getDescription())
                .status(project.getStatus())
                .startDate(project.getStartDate())
                .endDate(project.getEndDate())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }
}
