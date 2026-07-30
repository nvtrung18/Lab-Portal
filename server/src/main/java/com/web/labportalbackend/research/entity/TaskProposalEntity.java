package com.web.labportalbackend.research.entity;

import com.web.labportalbackend.common.entity.BaseEntity;
import com.web.labportalbackend.research.enums.TaskProposalStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "research_task_proposal", indexes = {
        @Index(name = "idx_task_proposal_proposer_status_created", columnList = "proposed_by, status, created_at, id"),
        @Index(name = "idx_task_proposal_group_status_created", columnList = "group_id, status, created_at, id"),
        @Index(name = "idx_task_proposal_project_status_created", columnList = "project_id, status, created_at, id"),
        @Index(name = "idx_task_proposal_ai_suggestion", columnList = "ai_action_suggestion_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskProposalEntity extends BaseEntity {

    @Column(name = "proposed_by", nullable = false)
    private Long proposedById;

    @Column(name = "reviewed_by")
    private Long reviewedById;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "milestone_id")
    private Long milestoneId;

    @Column(name = "ai_action_suggestion_id")
    private Long aiActionSuggestionId;

    @Builder.Default
    @Column(name = "assisted_by_ai", nullable = false)
    private Boolean assistedByAi = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "json")
    private String payloadJson;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskProposalStatus status = TaskProposalStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;
}
