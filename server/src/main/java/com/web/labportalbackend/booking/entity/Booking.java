package com.web.labportalbackend.booking.entity;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.common.entity.BaseEntity;
import com.web.labportalbackend.common.enums.BookingStatus;
import com.web.labportalbackend.lab.entity.Laboratory;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents a lab booking / reservation.
 */
@Entity
@Table(name = "bookings", indexes = {
        @Index(name = "idx_booking_user", columnList = "user_id"),
        @Index(name = "idx_booking_lab", columnList = "lab_id"),
        @Index(name = "idx_booking_slot", columnList = "slot_id"),
        @Index(name = "idx_booking_status", columnList = "status"),
        @Index(name = "idx_booking_time_range", columnList = "start_time, end_time"),
        @Index(name = "idx_booking_slot_user", columnList = "slot_id, user_id")
},
uniqueConstraints = {
        @UniqueConstraint(name = "uk_booking_user_slot", columnNames = {"user_id", "slot_id", "deleted"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Booking extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lab_id", nullable = false)
    private Laboratory lab;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id", nullable = true)
    private com.web.labportalbackend.booking.entity.TimeSlot timeSlot;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status = BookingStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String purpose;

    @Column(nullable = false)
    private Integer participantsCount = 1;
}
