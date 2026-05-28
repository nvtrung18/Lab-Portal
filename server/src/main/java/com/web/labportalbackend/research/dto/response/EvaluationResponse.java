package com.web.labportalbackend.research.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
@Schema(description = "Research student evaluation")
public class EvaluationResponse {
    private Long id;
    private Long projectId;
    private Long groupId;
    private String groupName;
    private Long studentId;
    private String studentName;
    private Long evaluatorId;
    private String evaluatorName;
    private BigDecimal attendanceScore;
    private BigDecimal taskScore;
    private BigDecimal reportScore;
    private BigDecimal productScore;
    private BigDecimal attitudeScore;
    private BigDecimal totalScore;
    private String lecturerComment;
    private Instant createdAt;
    private Instant updatedAt;
}
