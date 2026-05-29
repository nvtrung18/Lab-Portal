package com.web.labportalbackend.research.entity;

import com.web.labportalbackend.common.entity.BaseEntity;
import com.web.labportalbackend.research.enums.ResearchLogType;
import com.web.labportalbackend.research.enums.ResearchLogVisibility;
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

import java.time.LocalDate;

@Entity
@Table(name = "research_logs", indexes = {
        @Index(name = "idx_research_logs_project_created", columnList = "project_id, created_at"),
        @Index(name = "idx_research_logs_group", columnList = "group_id"),
        @Index(name = "idx_research_logs_author", columnList = "author_id"),
        @Index(name = "idx_research_logs_type", columnList = "log_type")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResearchLogEntity extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "group_id")
    private Long groupId;

    @Column(name = "milestone_id")
    private Long milestoneId;

    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    @Column(name = "author_name", length = 150)
    private String authorName;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "log_type", nullable = false, length = 20)
    private ResearchLogType logType = ResearchLogType.MANUAL;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String result;

    @Column(columnDefinition = "TEXT")
    private String problem;

    @Column(name = "next_plan", columnDefinition = "TEXT")
    private String nextPlan;

    @Column(name = "evidence_link", length = 1000)
    private String evidenceLink;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResearchLogVisibility visibility = ResearchLogVisibility.GROUP;
}
