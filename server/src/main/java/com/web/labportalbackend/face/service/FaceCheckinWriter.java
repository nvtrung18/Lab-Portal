package com.web.labportalbackend.face.service;

import com.web.labportalbackend.booking.entity.Booking;
import com.web.labportalbackend.booking.repository.BookingRepository;
import com.web.labportalbackend.booking.service.CheckinWindowPolicy;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.enums.BookingStatus;
import com.web.labportalbackend.common.enums.TimeSlotStatus;
import com.web.labportalbackend.face.entity.FaceCheckinLogEntity;
import com.web.labportalbackend.face.enums.FaceCheckinMethod;
import com.web.labportalbackend.face.enums.FaceCheckinResult;
import com.web.labportalbackend.face.repository.FaceCheckinLogRepository;
import com.web.labportalbackend.notification.enums.NotificationEventType;
import com.web.labportalbackend.notification.enums.NotificationTargetModule;
import com.web.labportalbackend.notification.service.NotificationEmitter;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class FaceCheckinWriter {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final FaceCheckinLogRepository checkinLogRepository;
    private final CheckinWindowPolicy checkinWindowPolicy;
    private final NotificationEmitter notificationEmitter;

    @Transactional
    public Instant complete(Long studentId, Long managerId, Long bookingId,
                            Double confidenceScore, Double livenessScore) {
        Booking booking = ownedBooking(studentId, bookingId);
        User manager = userRepository.findById(managerId)
                .orElseThrow(() -> new AccessDeniedException("Laboratory manager not found"));
        requireManagedLab(booking, managerId);
        Instant checkedInAt = Instant.now();
        validateBookingAndWindow(booking, checkedInAt);
        booking.setStatus(BookingStatus.CHECKED_IN);
        checkinLogRepository.save(log(booking, FaceCheckinResult.SUCCESS, confidenceScore,
                livenessScore, null, manager));
        notificationEmitter.emit(studentId, NotificationEventType.FACE_CHECKIN_SUCCEEDED,
                "Face check-in succeeded", "Your face check-in was accepted",
                NotificationTargetModule.FACE, bookingId, null);
        return checkedInAt;
    }

    @Transactional
    public void recordFailure(
            Long studentId,
            Long managerId,
            Long bookingId,
            Double confidenceScore,
            Double livenessScore,
            String failureReason
    ) {
        Booking booking = ownedBooking(studentId, bookingId);
        User manager = userRepository.findById(managerId)
                .orElseThrow(() -> new AccessDeniedException("Laboratory manager not found"));
        requireManagedLab(booking, managerId);
        validateBookingAndWindow(booking, Instant.now());
        checkinLogRepository.save(log(booking, FaceCheckinResult.FAILED, confidenceScore,
                livenessScore, failureReason, manager));
        notificationEmitter.emit(studentId, NotificationEventType.FACE_CHECKIN_FAILED,
                "Face check-in failed", "Your face check-in was not accepted",
                NotificationTargetModule.FACE, bookingId, null);
    }

    private Booking ownedBooking(Long actorId, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found"));
        if (!booking.getUser().getId().equals(actorId)) {
            throw new AccessDeniedException("Face check-in is limited to the booking owner");
        }
        return booking;
    }

    private void validateBookingAndWindow(Booking booking, Instant now) {
        if (booking.getStatus() != BookingStatus.APPROVED) {
            throw new IllegalStateException("Only an approved booking can be checked in");
        }
        if (booking.getTimeSlot() == null || booking.getTimeSlot().getStatus() == TimeSlotStatus.CANCELLED) {
            throw new IllegalStateException("Booking time slot is not available");
        }
        checkinWindowPolicy.validate(booking, now);
    }

    private void requireManagedLab(Booking booking, Long managerId) {
        if (booking.getLab() == null || booking.getLab().getManager() == null
                || !managerId.equals(booking.getLab().getManager().getId())) {
            throw new AccessDeniedException("Booking does not belong to a laboratory managed by this user");
        }
    }

    private FaceCheckinLogEntity log(
            Booking booking,
            FaceCheckinResult result,
            Double confidenceScore,
            Double livenessScore,
            String failureReason,
            User checkedInBy
    ) {
        return FaceCheckinLogEntity.builder()
                .booking(booking)
                .user(booking.getUser())
                .lab(booking.getLab())
                .checkedInBy(checkedInBy)
                .checkinMethod(FaceCheckinMethod.FACE)
                .result(result)
                .confidenceScore(decimal(confidenceScore))
                .livenessScore(decimal(livenessScore))
                .failureReason(failureReason)
                .build();
    }

    private BigDecimal decimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }
}
