package com.web.labportalbackend.booking.service.impl;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.booking.dto.response.WaitlistResponse;
import com.web.labportalbackend.booking.entity.Booking;
import com.web.labportalbackend.booking.entity.TimeSlot;
import com.web.labportalbackend.booking.entity.WaitlistEntity;
import com.web.labportalbackend.booking.mapper.WaitlistMapper;
import com.web.labportalbackend.booking.repository.BookingRepository;
import com.web.labportalbackend.booking.repository.TimeSlotRepository;
import com.web.labportalbackend.booking.repository.WaitlistRepository;
import com.web.labportalbackend.booking.service.WaitlistService;
import com.web.labportalbackend.common.enums.BookingStatus;
import com.web.labportalbackend.common.enums.WaitlistStatus;
import com.web.labportalbackend.common.exception.WaitlistDuplicateException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementation of WaitlistService with race-condition-safe position assignment.
 * <p>
 * Position assignment strategy:
 * 1. Query MAX(position) using PESSIMISTIC_WRITE lock (3s timeout)
 * 2. Calculate newPosition = MAX + 1 (or 1 if no entries)
 * 3. Create and save WaitlistEntity
 * 4. Return WaitlistResponse
 * <p>
 * UNIQUE constraint on (slot_id, user_id) ensures no duplicate entries at DB level.
 * Pessimistic locking prevents race conditions when multiple threads increment position.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WaitlistServiceImpl implements WaitlistService {

    private final WaitlistRepository waitlistRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;

    @Override
    @Transactional
    public WaitlistResponse addToWaitlist(Long userId, Long slotId) {
        log.debug("Adding user {} to waitlist for slot {}", userId, slotId);

        // Validate user exists
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + userId));

        // Validate slot exists
        TimeSlot timeSlot = timeSlotRepository.findById(slotId)
                .orElseThrow(() -> new EntityNotFoundException("Time slot not found with ID: " + slotId));

        // Check if user already in waitlist (catches UNIQUE constraint violation early)
        if (waitlistRepository.existsUserInWaitlist(userId, slotId)) {
            throw new WaitlistDuplicateException(userId, slotId);
        }

        // Race-condition-safe position assignment:
        // findMaxPositionBySlotId() uses @Lock(PESSIMISTIC_WRITE) to prevent concurrent increments
        Integer maxPosition = waitlistRepository.findMaxPositionBySlotId(slotId).orElse(0);
        Integer newPosition = maxPosition + 1;

        log.debug("Calculated new position {} for user {} in slot {}", newPosition, userId, slotId);

        // Create and save WaitlistEntity
        WaitlistEntity waitlist = WaitlistEntity.builder()
                .timeSlot(timeSlot)
                .user(user)
                .position(newPosition)
                .build();

        WaitlistEntity saved = waitlistRepository.save(waitlist);
        log.info("User {} added to waitlist at position {} for slot {}", userId, newPosition, slotId);

        return WaitlistMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WaitlistResponse> getWaitlistBySlot(Long slotId) {
        log.debug("Fetching waitlist for slot {}", slotId);

        // Validate slot exists
        if (!timeSlotRepository.existsById(slotId)) {
            throw new EntityNotFoundException("Time slot not found with ID: " + slotId);
        }

        return waitlistRepository.findBySlotIdOrderByPosition(slotId)
                .stream()
                .map(WaitlistMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void promoteNext(Long slotId) {
        log.debug("Attempting to promote next user in waitlist for slot {}", slotId);

        // Find the first user in waitlist with PENDING status
        // Uses PESSIMISTIC_WRITE lock to ensure only one thread promotes at a time
        var nextUser = waitlistRepository.findFirstBySlotIdAndStatusOrderByPositionAsc(
                slotId, WaitlistStatus.PENDING);

        if (nextUser.isEmpty()) {
            log.debug("No pending users in waitlist for slot {}", slotId);
            return;
        }

        WaitlistEntity waitlistEntry = nextUser.get();
        User user = waitlistEntry.getUser();
        Long userId = user.getId();

        log.debug("Found next user {} at position {} for slot {}", userId, waitlistEntry.getPosition(), slotId);

        // EDGE CASE: Check if user already has an active booking for this slot
        // This can happen due to data inconsistency or concurrent operations
        if (bookingRepository.existsActiveBookingByUserAndSlot(userId, slotId)) {
            log.warn("User {} already has active booking for slot {}. Marking waitlist as PROMOTED and trying next.", userId, slotId);
            waitlistEntry.setStatus(WaitlistStatus.PROMOTED);
            waitlistRepository.save(waitlistEntry);
            
            // Recursively try the next user
            promoteNext(slotId);
            return;
        }

        // Get time slot details for creating booking
        TimeSlot timeSlot = timeSlotRepository.findById(slotId)
                .orElseThrow(() -> new EntityNotFoundException("Time slot not found: " + slotId));

        // Create new CONFIRMED booking for the promoted user
        Booking newBooking = new Booking();
        newBooking.setUser(user);
        newBooking.setLab(timeSlot.getLab());
        newBooking.setTimeSlot(timeSlot);
        newBooking.setStartTime(timeSlot.getStartTime());
        newBooking.setEndTime(timeSlot.getEndTime());
        newBooking.setStatus(BookingStatus.CONFIRMED);
        newBooking.setParticipantsCount(1);

        Booking savedBooking = bookingRepository.save(newBooking);
        log.debug("Created confirmed booking {} for user {} from waitlist", savedBooking.getId(), userId);

        // Mark waitlist entry as PROMOTED
        waitlistEntry.setStatus(WaitlistStatus.PROMOTED);
        waitlistRepository.save(waitlistEntry);

        log.info("User {} promoted to confirmed booking {} for slot {}", userId, savedBooking.getId(), slotId);
    }
}
