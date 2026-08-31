package com.web.labportalbackend.face.service;

import com.web.labportalbackend.admin.systemconfig.dto.SystemConfigResponse;
import com.web.labportalbackend.admin.systemconfig.service.SystemConfigService;
import com.web.labportalbackend.booking.entity.Booking;
import com.web.labportalbackend.booking.repository.BookingRepository;
import com.web.labportalbackend.common.enums.BookingStatus;
import com.web.labportalbackend.common.enums.TimeSlotStatus;
import com.web.labportalbackend.face.entity.FaceCheckinLogEntity;
import com.web.labportalbackend.face.enums.FaceCheckinMethod;
import com.web.labportalbackend.face.enums.FaceCheckinResult;
import com.web.labportalbackend.face.repository.FaceCheckinLogRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class FaceCheckinWriter {

    private final BookingRepository bookingRepository;
    private final FaceCheckinLogRepository checkinLogRepository;
    private final SystemConfigService systemConfigService;

    @Transactional
    public Instant complete(Long actorId, Long bookingId, Double confidenceScore, Double livenessScore) {
        Booking booking = ownedBooking(actorId, bookingId);
        Instant checkedInAt = Instant.now();
        validateBookingAndWindow(booking, checkedInAt);
        booking.setStatus(BookingStatus.CHECKED_IN);
        checkinLogRepository.save(log(booking, FaceCheckinResult.SUCCESS, confidenceScore,
                livenessScore, null));
        return checkedInAt;
    }

    @Transactional
    public void recordFailure(
            Long actorId,
            Long bookingId,
            Double confidenceScore,
            Double livenessScore,
            String failureReason
    ) {
        Booking booking = ownedBooking(actorId, bookingId);
        validateBookingAndWindow(booking, Instant.now());
        checkinLogRepository.save(log(booking, FaceCheckinResult.FAILED, confidenceScore,
                livenessScore, failureReason));
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
        SystemConfigResponse config = systemConfigService.getConfig();
        if (config == null || config.booking() == null || config.booking().checkinWindowMinutes() <= 0) {
            throw new IllegalStateException("Check-in window is not configured");
        }
        Instant end = booking.getStartTime().plus(
                Duration.ofMinutes(config.booking().checkinWindowMinutes()));
        if (now.isBefore(booking.getStartTime()) || now.isAfter(end)) {
            throw new IllegalStateException("Booking is outside the check-in window");
        }
    }

    private FaceCheckinLogEntity log(
            Booking booking,
            FaceCheckinResult result,
            Double confidenceScore,
            Double livenessScore,
            String failureReason
    ) {
        return FaceCheckinLogEntity.builder()
                .booking(booking)
                .user(booking.getUser())
                .lab(booking.getLab())
                .checkedInBy(booking.getUser())
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
