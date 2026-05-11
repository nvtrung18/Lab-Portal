package com.web.labportalbackend.booking;

import com.web.labportalbackend.booking.entity.Booking;
import com.web.labportalbackend.booking.entity.TimeSlot;
import com.web.labportalbackend.booking.mapper.BookingMapper;
import com.web.labportalbackend.booking.repository.BookingRepository;
import com.web.labportalbackend.booking.repository.TimeSlotRepository;
import com.web.labportalbackend.common.dto.BookingResponse;
import com.web.labportalbackend.common.dto.CreateBookingRequest;
import com.web.labportalbackend.common.enums.BookingStatus;
import com.web.labportalbackend.common.exception.SlotFullException;
import com.web.labportalbackend.common.exception.DuplicateBookingException;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.dao.PessimisticLockingFailureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service implementation for booking management.
 * Handles booking logic with capacity validation and persistence.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final UserRepository userRepository;
    private final BookingCoreService bookingCoreService;  // Self-injection for @Transactional to work

    // Constants for retry logic
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 150;  // Sleep between retries

    /**
     * Create a new booking with pessimistic locking, retry logic, and fallback to waitlist.
     * 
     * This method implements high-concurrency handling:
     * 1. Attempts to lock slot and create CONFIRMED booking (up to 3 retries)
     * 2. On lock timeout/failure, sleeps and retries
     * 3. On SlotFullException or retry exhaustion, creates WAITLISTED booking
     * 4. Returns response indicating booking status (CONFIRMED or WAITLISTED)
     * 
     * IMPORTANT: This method is NOT annotated with @Transactional.
     * It delegates to BookingCoreService.lockSlotAndBook() which has @Transactional.
     * This separation is critical for Spring AOP proxy to work correctly.
     * 
     * @param userId the user ID
     * @param request the booking request
     * @return the created booking response (status = CONFIRMED or WAITLISTED)
     * @throws EntityNotFoundException if user or slot not found
     * @throws DuplicateBookingException if user already has active booking for slot
     */
    @Override
    public BookingResponse book(Long userId, CreateBookingRequest request) {
        log.debug("Starting booking process for user {} on slot {}", userId, request.getSlotId());

        // 1. Pre-validate user and slot existence
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + userId));

        TimeSlot timeSlot = timeSlotRepository.findById(request.getSlotId())
                .orElseThrow(() -> new EntityNotFoundException("Time slot not found with ID: " + request.getSlotId()));

        // 2. Check for duplicate booking (prevent user from booking same slot twice)
        if (bookingRepository.existsActiveBookingByUserAndSlot(userId, request.getSlotId())) {
            log.warn("Duplicate booking attempt: User {} trying to book slot {} again", userId, request.getSlotId());
            throw new DuplicateBookingException(userId, request.getSlotId());
        }

        // 3. Attempt to lock slot and create CONFIRMED booking with retry logic
        BookingResponse response = attemptConfirmedBookingWithRetry(userId, request.getSlotId(), user);
        if (response != null) {
            log.info("Booking CONFIRMED for user {} on slot {}", userId, request.getSlotId());
            return response;
        }

        // 4. Fallback: Create WAITLISTED booking
        log.warn("All retry attempts exhausted. Creating WAITLISTED booking for user {} on slot {}", userId, request.getSlotId());
        return createWaitlistedBooking(user, timeSlot);
    }

    /**
     * Attempt to create a CONFIRMED booking with pessimistic locking and retry logic.
     * 
     * Flow:
     * 1. Try up to 3 times to acquire lock and create booking
     * 2. If lock failure exception caught, sleep and retry
     * 3. If SlotFullException caught on any attempt, return null to trigger waitlist
     * 4. Return response if successful
     * 
     * @param userId the user ID
     * @param slotId the slot ID
     * @param user the User entity
     * @return booking response if successful, null if slot is full
     */
    private BookingResponse attemptConfirmedBookingWithRetry(Long userId, Long slotId, User user) {
        int attemptCount = 0;

        while (attemptCount < MAX_RETRIES) {
            attemptCount++;
            log.debug("Attempt {} of {} to acquire lock for slot {}", attemptCount, MAX_RETRIES, slotId);

            try {
                // Call BookingCoreService.lockSlotAndBook() which is @Transactional
                return bookingCoreService.lockSlotAndBook(userId, slotId, user);

            } catch (SlotFullException e) {
                // Slot is full - signal to create waitlist booking
                log.debug("Slot {} is full on attempt {}. Will create WAITLISTED booking.", slotId, attemptCount);
                return null;

            } catch (PessimisticLockingFailureException e) {
                // Lock timeout or contention - retry with backoff
                if (attemptCount < MAX_RETRIES) {
                    long jitter = (long) (Math.random() * 100);  // 0-100ms jitter
                    long sleepTime = RETRY_DELAY_MS + jitter;
                    log.debug("Lock acquisition failed on attempt {} for slot {}. Sleeping {}ms before retry.",
                            attemptCount, slotId, sleepTime);
                    try {
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("Retry sleep interrupted for slot {}", slotId);
                        return null;  // Fall back to waitlist
                    }
                } else {
                    log.warn("Lock acquisition failed after {} attempts for slot {}. Giving up.", MAX_RETRIES, slotId);
                    return null;  // Trigger waitlist on final failure
                }

            } catch (JpaSystemException e) {
                // Catch lock timeout as JpaSystemException as well (database-dependent)
                if (e.getCause() instanceof PessimisticLockingFailureException) {
                    if (attemptCount < MAX_RETRIES) {
                        log.debug("JPA lock timeout on attempt {} for slot {}. Retrying...", attemptCount, slotId);
                        try {
                            Thread.sleep(RETRY_DELAY_MS);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            return null;
                        }
                    } else {
                        log.warn("JPA lock timeout after {} attempts for slot {}", MAX_RETRIES, slotId);
                        return null;
                    }
                } else {
                    throw e;  // Re-throw other JPA errors
                }
            }
        }

        log.debug("Exhausted all {} retry attempts for slot {}", MAX_RETRIES, slotId);
        return null;  // Trigger waitlist
    }

    /**
     * Create a WAITLISTED booking for user when CONFIRMED booking fails.
     * This ensures no user is turned away during high-concurrency scenarios.
     * 
     * @param user the User entity
     * @param timeSlot the TimeSlot entity
     * @return booking response with WAITLISTED status
     */
    @Transactional
    private BookingResponse createWaitlistedBooking(User user, TimeSlot timeSlot) {
        log.debug("Creating WAITLISTED booking for user {} on slot {}", user.getId(), timeSlot.getId());

        Booking booking = new Booking();
        booking.setUser(user);
        booking.setLab(timeSlot.getLab());
        booking.setTimeSlot(timeSlot);
        booking.setStartTime(timeSlot.getStartTime());
        booking.setEndTime(timeSlot.getEndTime());
        booking.setStatus(BookingStatus.WAITLISTED);
        booking.setParticipantsCount(1);

        Booking saved = bookingRepository.save(booking);
        log.info("WAITLISTED booking created. Booking ID: {}, User ID: {}, Slot ID: {}",
                saved.getId(), user.getId(), timeSlot.getId());

        return BookingMapper.toResponse(saved);
    }

    /**
     * Retrieve all bookings with pagination.
     */
    @Override
    @Transactional(readOnly = true)
    public Page<BookingResponse> getAllBookings(Pageable pageable) {
        log.debug("Fetching all bookings with pagination. Page: {}, Size: {}", 
                pageable.getPageNumber(), pageable.getPageSize());

        return bookingRepository.findAll(pageable)
                .map(BookingMapper::toResponse);
    }

    /**
     * Retrieve bookings for a specific user.
     */
    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> getBookingsByUser(Long userId) {
        log.debug("Fetching bookings for user: {}", userId);

        // Validate user exists
        if (!userRepository.existsById(userId)) {
            throw new EntityNotFoundException("User not found with ID: " + userId);
        }

        return bookingRepository.findByUserId(userId).stream()
                .map(BookingMapper::toResponse)
                .toList();
    }

    /**
     * Retrieve bookings for a specific time slot.
     */
    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> getBookingsBySlot(Long slotId) {
        log.debug("Fetching bookings for slot: {}", slotId);

        // Validate slot exists
        if (!timeSlotRepository.existsById(slotId)) {
            throw new EntityNotFoundException("Time slot not found with ID: " + slotId);
        }

        return bookingRepository.findBySlotId(slotId).stream()
                .map(BookingMapper::toResponse)
                .toList();
    }

    /**
     * Cancel a booking with ownership and state validation.
     * Logic:
     * 1. Find booking by ID
     * 2. Verify user owns the booking
     * 3. Check current status (cannot cancel already cancelled booking)
     * 4. Update status to CANCELLED and save
     * 
     * When a booking is cancelled, it no longer counts toward capacity,
     * allowing other users to book the freed slot.
     *
     * @param bookingId the booking ID
     * @param userId the user ID requesting cancellation
     * @throws EntityNotFoundException if booking not found
     * @throws AccessDeniedException if user doesn't own the booking
     * @throws IllegalStateException if booking is already cancelled
     */
    @Transactional
    public void cancelBooking(Long bookingId, Long userId) {
        log.debug("Cancelling booking {} for user {}", bookingId, userId);

        // 1. Find booking
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found with ID: " + bookingId));

        // 2. Verify ownership
        if (!booking.getUser().getId().equals(userId)) {
            log.warn("User {} attempted to cancel booking {} owned by user {}", 
                    userId, bookingId, booking.getUser().getId());
            throw new AccessDeniedException("You can only cancel your own bookings");
        }

        // 3. Check current status
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            log.warn("Booking {} is already cancelled", bookingId);
            throw new IllegalStateException("Booking is already cancelled");
        }

        // 4. Update status and save
        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
        log.info("Booking {} cancelled successfully by user {}", bookingId, userId);
    }

    /**
     * Legacy method for backward compatibility.
     * Cancels booking without ownership verification.
     * 
     * @deprecated Use cancelBooking(Long bookingId, Long userId) instead
     */
    @Override
    @Transactional
    @Deprecated(since = "1.0", forRemoval = true)
    public void cancelBooking(Long bookingId) {
        log.debug("Cancelling booking: {}", bookingId);

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found with ID: " + bookingId));

        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
        log.info("Booking {} cancelled successfully", bookingId);
    }

}
