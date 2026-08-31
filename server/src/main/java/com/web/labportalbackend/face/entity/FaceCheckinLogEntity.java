package com.web.labportalbackend.face.entity;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.booking.entity.Booking;
import com.web.labportalbackend.face.enums.FaceCheckinMethod;
import com.web.labportalbackend.face.enums.FaceCheckinResult;
import com.web.labportalbackend.lab.entity.Laboratory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "face_checkin_log", indexes = {
        @Index(name = "idx_face_checkin_log_booking_created", columnList = "booking_id, created_at, id"),
        @Index(name = "idx_face_checkin_log_user_created", columnList = "user_id, created_at, id"),
        @Index(name = "idx_face_checkin_log_lab_created", columnList = "lab_id, created_at, id"),
        @Index(name = "idx_face_checkin_log_method_result_created",
                columnList = "checkin_method, result, created_at, id")
})
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FaceCheckinLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lab_id", nullable = false)
    private Laboratory lab;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "checked_in_by", nullable = false)
    private User checkedInBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "checkin_method", nullable = false, length = 30)
    private FaceCheckinMethod checkinMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FaceCheckinResult result;

    @Column(name = "confidence_score", precision = 5, scale = 4)
    private BigDecimal confidenceScore;

    @Column(name = "liveness_score", precision = 5, scale = 4)
    private BigDecimal livenessScore;

    @Column(name = "failure_reason", length = 50)
    private String failureReason;

    @Column(name = "fallback_reason", columnDefinition = "TEXT")
    private String fallbackReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (checkinMethod != FaceCheckinMethod.FACE
                && (fallbackReason == null || fallbackReason.isBlank())) {
            throw new IllegalStateException("Fallback reason is required");
        }
    }
}
