package com.web.labportalbackend.research.mapper;

import com.web.labportalbackend.research.dto.response.EvaluationResponse;
import com.web.labportalbackend.research.entity.EvaluationEntity;
import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.auth.entity.User;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EvaluationMapper {

    public static EvaluationResponse toResponse(EvaluationEntity evaluation) {
        return toResponse(evaluation, null, null, null);
    }

    public static EvaluationResponse toResponse(
            EvaluationEntity evaluation,
            User student,
            User evaluator,
            GroupEntity group
    ) {
        return EvaluationResponse.builder()
                .id(evaluation.getId())
                .projectId(evaluation.getProjectId())
                .groupId(evaluation.getGroupId())
                .groupName(group == null ? null : group.getName())
                .studentId(evaluation.getStudentId())
                .studentName(student == null ? null : student.getFullName())
                .evaluatorId(evaluation.getEvaluatorId())
                .evaluatorName(evaluator == null ? null : evaluator.getFullName())
                .contributionScore(evaluation.getContributionScore())
                .taskScore(evaluation.getTaskScore())
                .reportScore(evaluation.getReportScore())
                .productScore(evaluation.getProductScore())
                .attitudeScore(evaluation.getAttitudeScore())
                .totalScore(evaluation.getTotalScore())
                .lecturerComment(evaluation.getLecturerComment())
                .createdAt(evaluation.getCreatedAt())
                .updatedAt(evaluation.getUpdatedAt())
                .build();
    }
}
