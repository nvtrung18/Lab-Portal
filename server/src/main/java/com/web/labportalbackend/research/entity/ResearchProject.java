package com.web.labportalbackend.research.entity;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.common.entity.BaseEntity;
import com.web.labportalbackend.common.enums.ResearchStatus;
import com.web.labportalbackend.lab.entity.Laboratory;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Represents a research project conducted in a laboratory.
 */
@Entity
@Table(name = "research_projects", indexes = {
        @Index(name = "idx_research_name", columnList = "project_name", unique = true),
        @Index(name = "idx_research_lab", columnList = "lab_id"),
        @Index(name = "idx_research_leader", columnList = "leader_id"),
        @Index(name = "idx_research_status", columnList = "status"),
        @Index(name = "idx_research_domain", columnList = "domain")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResearchProject extends BaseEntity {

    @Column(name = "project_name", unique = true, nullable = false, length = 200)
    private String projectName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lab_id", nullable = false)
    private Laboratory lab;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leader_id", nullable = false)
    private User leader;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ResearchStatus status = ResearchStatus.PLANNING;

    @Column(length = 100)
    private String domain;

    @Column(nullable = false)
    private Integer teamSize = 1;

    @Column(columnDefinition = "TEXT")
    private String objectives;

    private LocalDate startDate;

    private LocalDate endDate;
}
