package com.web.labportalbackend.booking.entity;

import com.web.labportalbackend.common.entity.BaseEntity;
import com.web.labportalbackend.common.enums.TimeSlotStatus;
import com.web.labportalbackend.lab.entity.Laboratory;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Represents a time slot for lab availability and booking.
 * TimeSlot operates independently from Booking to allow flexible slot management.
 */
@Entity
@Table(name = "time_slots", indexes = {
        @Index(name = "idx_time_slots_lab_id", columnList = "lab_id"),
        @Index(name = "idx_time_slots_status", columnList = "status"),
        @Index(name = "idx_time_slots_time_range", columnList = "start_time, end_time"),
        @Index(name = "idx_time_slots_deleted", columnList = "deleted"),
        @Index(name = "idx_time_slots_active", columnList = "active"),
        @Index(name = "idx_time_slots_lab_time", columnList = "lab_id, start_time, end_time")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimeSlot extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lab_id", nullable = false)
    private Laboratory lab;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(nullable = false)
    private Integer capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TimeSlotStatus status = TimeSlotStatus.AVAILABLE;
}
