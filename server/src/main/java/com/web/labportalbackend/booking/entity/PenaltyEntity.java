package com.web.labportalbackend.booking.entity;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.common.entity.BaseEntity;
import com.web.labportalbackend.common.enums.PenaltyStatus;
import com.web.labportalbackend.common.enums.PenaltyType;
import com.web.labportalbackend.lab.entity.Laboratory;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "penalties", indexes = {
        @Index(name = "idx_penalty_user", columnList = "user_id"),
        @Index(name = "idx_penalty_lab", columnList = "lab_id"),
        @Index(name = "idx_penalty_slot", columnList = "slot_id"),
        @Index(name = "idx_penalty_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PenaltyEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lab_id", nullable = false)
    private Laboratory lab;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id", nullable = false)
    private TimeSlot slot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private PenaltyType type = PenaltyType.OTHER;

    @Column(nullable = false)
    @Builder.Default
    private Integer point = 0;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PenaltyStatus status = PenaltyStatus.ACTIVE;

    @OneToOne(mappedBy = "penalty", fetch = FetchType.LAZY)
    private ComplaintEntity complaint;
}
