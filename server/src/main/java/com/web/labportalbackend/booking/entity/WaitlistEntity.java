package com.web.labportalbackend.booking.entity;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a user's position in a time slot's waitlist.
 * <p>
 * When a time slot is full and a user attempts to book, they are added to this waitlist
 * with an auto-calculated position number. Position is guaranteed unique and monotonically
 * increasing via pessimistic locking at the database level.
 * <p>
 * UNIQUE constraint on (slot_id, user_id) prevents duplicate entries.
 */
@Entity
@Table(
    name = "waitlists",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_waitlist_slot_user", columnNames = {"slot_id", "user_id"})
    },
    indexes = {
        @Index(name = "idx_waitlist_slot_position", columnList = "slot_id, position"),
        @Index(name = "idx_waitlist_user", columnList = "user_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WaitlistEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id", nullable = false)
    private TimeSlot timeSlot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Position in the waitlist queue (1-based indexing).
     * Assigned atomically via pessimistic locking to prevent race conditions.
     */
    @Column(nullable = false)
    private Integer position;
}
