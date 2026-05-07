package com.web.labportalbackend.lab.entity;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.common.entity.BaseEntity;
import com.web.labportalbackend.common.enums.ApplicationStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a CV application submitted by a user to a laboratory.
 * Contains the CV URL and application status (PENDING, REVIEWING, APPROVED, REJECTED).
 */
@Entity
@Table(name = "applications", indexes = {
        @Index(name = "idx_app_user", columnList = "user_id"),
        @Index(name = "idx_app_lab", columnList = "lab_id"),
        @Index(name = "idx_app_status", columnList = "status"),
        @Index(name = "idx_app_created", columnList = "created_at"),
        @Index(name = "idx_app_deleted", columnList = "deleted")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_app_user_lab", columnNames = {"user_id", "lab_id", "deleted"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Application extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false, referencedColumnName = "id")
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "lab_id", nullable = false, referencedColumnName = "id")
    private Laboratory laboratory;

    @Column(name = "cv_url", nullable = false, length = 500)
    private String cvUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status = ApplicationStatus.PENDING;
}
