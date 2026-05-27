package com.web.labportalbackend.research.entity;

import com.web.labportalbackend.common.entity.BaseEntity;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.research.enums.MilestoneStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "milestones", indexes = {
        @Index(name = "idx_milestone_project", columnList = "project_id"),
        @Index(name = "idx_milestone_project_start_date", columnList = "project_id, start_date"),
        @Index(name = "idx_milestone_project_deadline", columnList = "project_id, deadline")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MilestoneEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectEntity project;

    /**
     * Legacy timeline name retained while older task fixtures are migrated.
     */
    @Column(length = 200)
    private String name;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column
    private LocalDate deadline;

    @Builder.Default
    @Column(name = "progress_percent", nullable = false)
    private Integer progressPercent = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_student_id")
    private User assignedToStudent;

    @Column(name = "evidence_url", length = 1000)
    private String evidenceUrl;

    @Column(name = "manager_comment", columnDefinition = "TEXT")
    private String managerComment;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MilestoneStatus status = MilestoneStatus.NOT_STARTED;
}
