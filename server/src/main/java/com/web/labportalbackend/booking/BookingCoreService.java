package com.web.labportalbackend.booking;

import com.web.labportalbackend.booking.entity.Booking;
import com.web.labportalbackend.booking.entity.TimeSlot;
import com.web.labportalbackend.booking.mapper.BookingMapper;
import com.web.labportalbackend.booking.repository.BookingRepository;
import com.web.labportalbackend.booking.repository.TimeSlotRepository;
import com.web.labportalbackend.common.dto.BookingResponse;
import com.web.labportalbackend.common.enums.BookingStatus;
import com.web.labportalbackend.common.exception.SlotFullException;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core booking service responsible for pessimistic locking and capacity validation.
 * This service is separated from BookingService to ensure @Transactional works correctly.
 * 
 * IMPORTANT: This class MUST remain as a separate bean to avoid Spring AOP proxy issues.
 * When booking() in BookingServiceImpl calls lockSlotAndBook() through self-injection,
 * Spring's @Transactional annotation will work correctly.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingCoreService {

    private final BookingRepository bookingRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final UserRepository userRepository;

    /**
     * Lock a time slot and create a booking with capacity validation.
     * This method uses pessimistic locking to prevent race conditions.
     * 
     * CRITICAL: This method MUST be annotated with @Transactional.
     * It acquires an exclusive database lock on the TimeSlot row.
     * 
     * Flow:
     * 1. Lock TimeSlot using findByIdWithLock() (PESSIMISTIC_WRITE)
     * 2. Count CONFIRMED bookings for the slot
     * 3. If capacity exceeded, throw SlotFullException
     * 4. Otherwise, create and persist the booking
     * 
     * @param userId the user ID
     * @param slotId the time slot ID
     * @param user the User entity (pre-fetched)
     * @return the created booking response with CONFIRMED status
     * @throws EntityNotFoundException if slot not found
     * @throws SlotFullException if slot capacity is reached
     * @throws org.springframework.dao.PessimisticLockingFailureException if lock cannot be acquired
     */
    @Transactional(timeout = 5)
    public BookingResponse lockSlotAndBook(Long userId, Long slotId, User user) {
        log.debug("Acquiring pessimistic lock for slot {} and creating booking for user {}", slotId, userId);

        // 1. Lock TimeSlot - This acquires exclusive database lock
        TimeSlot timeSlot = timeSlotRepository.findByIdWithLock(slotId)
                .orElseThrow(() -> new EntityNotFoundException("Time slot not found with ID: " + slotId));

        log.debug("Successfully acquired lock on TimeSlot ID: {}", slotId);

        // 2. Count CONFIRMED bookings for the slot
        long confirmedBookingCount = bookingRepository.countByTimeSlotIdAndStatus(slotId, BookingStatus.CONFIRMED);
        log.debug("Slot {} has {} confirmed bookings, capacity: {}", slotId, confirmedBookingCount, timeSlot.getCapacity());

        // 3. Validate capacity
        if (confirmedBookingCount >= timeSlot.getCapacity()) {
            log.warn("Slot {} is full. Cannot create booking for user {}. CONFIRMED: {}, Capacity: {}",
                    slotId, userId, confirmedBookingCount, timeSlot.getCapacity());
            throw new SlotFullException(slotId, timeSlot.getCapacity(), (int) confirmedBookingCount);
        }

        // 4. Create booking with CONFIRMED status
        Booking booking = new Booking();
        booking.setUser(user);
        booking.setLab(timeSlot.getLab());
        booking.setTimeSlot(timeSlot);
        booking.setStartTime(timeSlot.getStartTime());
        booking.setEndTime(timeSlot.getEndTime());
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setParticipantsCount(1);

        Booking saved = bookingRepository.save(booking);
        log.info("Booking created with CONFIRMED status. Booking ID: {}, User ID: {}, Slot ID: {}",
                saved.getId(), userId, slotId);

        return BookingMapper.toResponse(saved);
    }
}
