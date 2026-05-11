package com.web.labportalbackend.booking;

import com.web.labportalbackend.booking.entity.Booking;
import com.web.labportalbackend.booking.entity.TimeSlot;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
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

    /**
     * Create a new booking with capacity validation and duplicate prevention.
     * Flow:
     * 1. Validate user exists
     * 2. Validate time slot exists
     * 3. Check for duplicate booking
     * 4. Check slot capacity (only count CONFIRMED bookings)
     * 5. Create and save booking
     * 
     * @param userId the user ID
     * @param request the booking request
     * @return the created booking response
     * @throws EntityNotFoundException if user or slot not found
     * @throws DuplicateBookingException if user already booked this slot
     * @throws SlotFullException if slot capacity is reached
     */
    @Override
    @Transactional
    public BookingResponse book(Long userId, CreateBookingRequest request) {
        log.debug("Booking slot {} for user {}", request.getSlotId(), userId);

        // 1. Validate user exists
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + userId));

        // 2. Validate time slot exists
        TimeSlot timeSlot = timeSlotRepository.findById(request.getSlotId())
                .orElseThrow(() -> new EntityNotFoundException("Time slot not found with ID: " + request.getSlotId()));

        // 3. Check for duplicate booking (application-level check)
        if (bookingRepository.existsActiveBookingByUserAndSlot(userId, request.getSlotId())) {
            log.warn("Duplicate booking attempt: User {} trying to book slot {} again", userId, request.getSlotId());
            throw new DuplicateBookingException(userId, request.getSlotId());
        }

        // 4. Check slot capacity (only count CONFIRMED bookings)
        long confirmedBookingCount = bookingRepository.countByTimeSlotIdAndStatus(request.getSlotId(), BookingStatus.CONFIRMED);
        if (confirmedBookingCount >= timeSlot.getCapacity()) {
            log.warn("Slot {} is full. Current CONFIRMED bookings: {}, Capacity: {}", 
                    request.getSlotId(), confirmedBookingCount, timeSlot.getCapacity());
            throw new SlotFullException(request.getSlotId(), timeSlot.getCapacity(), (int) confirmedBookingCount);
        }

        // 5. Create and save booking
        Booking booking = new Booking();
        booking.setUser(user);
        booking.setLab(timeSlot.getLab());
        booking.setTimeSlot(timeSlot);
        booking.setStartTime(timeSlot.getStartTime());
        booking.setEndTime(timeSlot.getEndTime());
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setParticipantsCount(1);

        try {
            Booking saved = bookingRepository.save(booking);
            log.info("Booking created successfully. Booking ID: {}, User ID: {}, Slot ID: {}", 
                    saved.getId(), userId, request.getSlotId());
            return mapToResponse(saved);
        } catch (DataIntegrityViolationException e) {
            // Database-level unique constraint violation (backup check)
            log.warn("Database unique constraint violation for user {} booking slot {}", userId, request.getSlotId());
            throw new DuplicateBookingException(userId, request.getSlotId());
        }
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
                .map(this::mapToResponse);
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
                .map(this::mapToResponse)
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
                .map(this::mapToResponse)
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

    // ---- Mapper ----

    /**
     * Map Booking entity to BookingResponse DTO.
     */
    private BookingResponse mapToResponse(Booking booking) {
        return BookingResponse.builder()
                .id(booking.getId())
                .userId(booking.getUser().getId())
                .labId(booking.getLab().getId())
                .slotId(booking.getTimeSlot() != null ? booking.getTimeSlot().getId() : null)
                .startTime(booking.getStartTime())
                .endTime(booking.getEndTime())
                .status(booking.getStatus())
                .purpose(booking.getPurpose())
                .participantsCount(booking.getParticipantsCount())
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .build();
    }
}
