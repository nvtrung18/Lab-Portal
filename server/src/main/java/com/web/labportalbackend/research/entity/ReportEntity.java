package com.web.labportalbackend.research.entity;

import com.web.labportalbackend.common.entity.BaseEntity;
import com.web.labportalbackend.research.enums.ReportStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "reports", indexes = {
        @Index(name = "idx_reports_task_id", columnList = "task_id"),
        @Index(name = "idx_reports_milestone_created", columnList = "milestone_id, created_at"),
        @Index(name = "idx_reports_submitter", columnList = "submitted_by_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_report_task_submitter_version", columnNames = {"task_id", "submitted_by_id", "version"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReportEntity extends BaseEntity {

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "group_id")
    private Long groupId;

    @Column(name = "milestone_id", nullable = false)
    private Long milestoneId;

    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "submitted_by_id")
    private Long submittedById;

    @Column(nullable = false)
    private Integer version;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "content_done", nullable = false, columnDefinition = "TEXT")
    private String contentDone;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String result;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String difficulty;

    @Column(name = "next_plan", nullable = false, columnDefinition = "TEXT")
    private String nextPlan;

    @Column(name = "self_assessment", nullable = false, columnDefinition = "TEXT")
    private String selfAssessment;

    @Column(name = "file_url", nullable = false, length = 500)
    private String fileUrl;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_type", length = 100)
    private String fileType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "evidence_link", length = 1000)
    private String evidenceLink;

    @Column(name = "leader_reviewer_id")
    private Long leaderReviewerId;

    @Column(name = "leader_reviewed_at")
    private Instant leaderReviewedAt;

    @Column(name = "leader_comment", columnDefinition = "TEXT")
    private String leaderComment;

    @Column(name = "manager_reviewer_id")
    private Long managerReviewerId;

    @Column(name = "manager_reviewed_at")
    private Instant managerReviewedAt;

    @Column(name = "manager_comment", columnDefinition = "TEXT")
    private String managerComment;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ReportStatus status = ReportStatus.SUBMITTED;

    @Column(name = "submission_scope", nullable = false, length = 120)
    private String submissionScope;
}
