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
                .labId(project.getLab() != null ? project.getLab().getId() : null)
                .groupId(project.getGroup() != null ? project.getGroup().getId() : null)
                .topicId(project.getTopic() != null ? project.getTopic().getId() : null)
                .code(project.getCode())
                .title(project.getTitle())
                .researchDirection(project.getResearchDirection())
                .description(project.getDescription())
                .objective(project.getObjective())
                .status(project.getStatus())
                .createdByName(project.getCreatedBy() != null
                        ? project.getCreatedBy().getFullName() != null
                            ? project.getCreatedBy().getFullName()
                            : project.getCreatedBy().getEmail()
                        : null)
                .managerName(project.getManager() != null
                        ? project.getManager().getFullName() != null
                            ? project.getManager().getFullName()
                            : project.getManager().getEmail()
                        : null)
                .priority(project.getPriority())
                .requiredProducts(project.getRequiredProducts())
                .evaluationCriteria(project.getEvaluationCriteria())
                .startDate(project.getStartDate())
                .endDate(project.getEndDate())
                .expectedEndDate(project.getEndDate())
                .createdAt(project.getCreatedAt())
                .build();
    }

    public static ProjectDetailResponse toDetailResponse(ProjectEntity project) {
        return ProjectDetailResponse.builder()
                .id(project.getId())
                .labId(project.getLab() != null ? project.getLab().getId() : null)
                .groupId(project.getGroup() != null ? project.getGroup().getId() : null)
                .topicId(project.getTopic() != null ? project.getTopic().getId() : null)
                .code(project.getCode())
                .title(project.getTitle())
                .researchDirection(project.getResearchDirection())
                .description(project.getDescription())
                .objective(project.getObjective())
                .status(project.getStatus())
                .createdByName(project.getCreatedBy() != null
                        ? project.getCreatedBy().getFullName() != null
                            ? project.getCreatedBy().getFullName()
                            : project.getCreatedBy().getEmail()
                        : null)
                .managerName(project.getManager() != null
                        ? project.getManager().getFullName() != null
                            ? project.getManager().getFullName()
                            : project.getManager().getEmail()
                        : null)
                .priority(project.getPriority())
                .requiredProducts(project.getRequiredProducts())
                .evaluationCriteria(project.getEvaluationCriteria())
                .startDate(project.getStartDate())
                .endDate(project.getEndDate())
                .expectedEndDate(project.getEndDate())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }
}
