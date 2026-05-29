package com.web.labportalbackend.research.entity;

import com.web.labportalbackend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "evaluations", indexes = {
        @Index(name = "idx_evaluations_project_id", columnList = "project_id"),
        @Index(name = "idx_evaluations_reviewer_id", columnList = "reviewer_id"),
        @Index(name = "idx_evaluations_student_id", columnList = "student_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvaluationEntity extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "group_id")
    private Long groupId;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "reviewer_id", nullable = false)
    private Long evaluatorId;

    @Column(name = "contribution_score", nullable = false, precision = 4, scale = 2)
    private BigDecimal contributionScore;

    @Column(name = "task_score", nullable = false, precision = 4, scale = 2)
    private BigDecimal taskScore;

    @Column(name = "report_score", nullable = false, precision = 4, scale = 2)
    private BigDecimal reportScore;

    @Column(name = "product_score", nullable = false, precision = 4, scale = 2)
    private BigDecimal productScore;

    @Column(name = "attitude_score", nullable = false, precision = 4, scale = 2)
    private BigDecimal attitudeScore;

    @Column(name = "score", nullable = false, precision = 5, scale = 2)
    private BigDecimal totalScore;

    @Column(name = "comments", columnDefinition = "TEXT")
    private String lecturerComment;
}
