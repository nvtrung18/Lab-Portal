package com.web.labportalbackend.booking.service.impl;

import com.web.labportalbackend.booking.dto.response.CheckinResponse;
import com.web.labportalbackend.booking.entity.Booking;
import com.web.labportalbackend.booking.mapper.BookingMapper;
import com.web.labportalbackend.booking.repository.BookingRepository;
import com.web.labportalbackend.booking.service.CheckinService;
import com.web.labportalbackend.common.enums.BookingStatus;
import com.web.labportalbackend.common.exception.InvalidCheckinTimeException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Implementation of CheckinService with time window validation.
 * 
 * Check-in window: [startTime - 15 minutes, endTime]
 * Only users with CONFIRMED bookings can check in.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckinServiceImpl implements CheckinService {

    private final BookingRepository bookingRepository;

    private static final long CHECKIN_WINDOW_BEFORE_START_MINUTES = 15;

    @Override
    @Transactional
    public CheckinResponse checkIn(Long bookingId, Long userId) {
        log.debug("Processing check-in for booking {} by user {}", bookingId, userId);

        // 1. Fetch and validate booking exists
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found with ID: " + bookingId));

        // 2. Verify ownership
        if (!booking.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("You can only check in to your own bookings");
        }

        // 3. Verify booking status is CONFIRMED
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new InvalidCheckinTimeException(bookingId, InvalidCheckinTimeException.Reason.INVALID_STATUS);
        }

        // 4. Verify already not checked in
        if (booking.getStatus() == BookingStatus.CHECKED_IN) {
            throw new IllegalStateException("Booking already checked in");
        }

        // 5. Validate time window
        Instant now = Instant.now();
        Instant startTime = booking.getStartTime();
        Instant endTime = booking.getEndTime();
        Instant checkinWindowStart = startTime.minus(CHECKIN_WINDOW_BEFORE_START_MINUTES, ChronoUnit.MINUTES);

        if (now.isBefore(checkinWindowStart)) {
            log.warn("Check-in too early for booking {}: now={}, window_start={}", bookingId, now, checkinWindowStart);
            throw new InvalidCheckinTimeException(bookingId, InvalidCheckinTimeException.Reason.TOO_EARLY);
        }

        if (now.isAfter(endTime)) {
            log.warn("Check-in too late for booking {}: now={}, end_time={}", bookingId, now, endTime);
            throw new InvalidCheckinTimeException(bookingId, InvalidCheckinTimeException.Reason.TOO_LATE);
        }

        // 6. Update booking status and timestamp
        booking.setStatus(BookingStatus.CHECKED_IN);
        booking.setUpdatedAt(Instant.now());
        Booking savedBooking = bookingRepository.save(booking);

        log.info("User {} successfully checked in to booking {}", userId, bookingId);

        // 7. Build response
        return CheckinResponse.builder()
                .bookingId(savedBooking.getId())
                .userId(savedBooking.getUser().getId())
                .labId(savedBooking.getLab().getId())
                .slotId(savedBooking.getTimeSlot() != null ? savedBooking.getTimeSlot().getId() : null)
                .checkedInAt(Instant.now())
                .startTime(savedBooking.getStartTime())
                .endTime(savedBooking.getEndTime())
                .status(savedBooking.getStatus())
                .message("Check-in successful")
                .createdAt(savedBooking.getCreatedAt())
                .updatedAt(savedBooking.getUpdatedAt())
                .build();
    }
}
