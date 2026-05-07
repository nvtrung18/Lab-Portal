package com.web.labportalbackend.lab.entity;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a user's membership in a laboratory.
 * Created when an application is approved, tracks member role and joining date.
 */
@Entity
@Table(name = "memberships", indexes = {
        @Index(name = "idx_membership_user", columnList = "user_id"),
        @Index(name = "idx_membership_lab", columnList = "lab_id"),
        @Index(name = "idx_membership_role", columnList = "role"),
        @Index(name = "idx_membership_created", columnList = "created_at"),
        @Index(name = "idx_membership_deleted", columnList = "deleted")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_membership_user_lab", columnNames = {"user_id", "lab_id", "deleted"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Membership extends BaseEntity {

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false, referencedColumnName = "id")
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "lab_id", nullable = false, referencedColumnName = "id")
    private Laboratory laboratory;

    @Column(nullable = false, length = 50)
    private String role = "MEMBER";
}
