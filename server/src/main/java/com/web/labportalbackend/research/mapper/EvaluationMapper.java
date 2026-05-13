package com.web.labportalbackend.research.mapper;

import com.web.labportalbackend.research.dto.response.EvaluationResponse;
import com.web.labportalbackend.research.entity.EvaluationEntity;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EvaluationMapper {

    public static EvaluationResponse toResponse(EvaluationEntity evaluation) {
        return EvaluationResponse.builder()
                .id(evaluation.getId())
                .projectId(evaluation.getProjectId())
                .reviewerId(evaluation.getReviewerId())
                .score(evaluation.getScore())
                .comments(evaluation.getComments())
                .createdAt(evaluation.getCreatedAt())
                .build();
    }
}
