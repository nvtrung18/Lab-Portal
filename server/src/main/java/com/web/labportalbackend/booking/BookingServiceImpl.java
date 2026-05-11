package com.web.labportalbackend.booking;

import com.web.labportalbackend.booking.entity.Booking;
import com.web.labportalbackend.booking.entity.TimeSlot;
import com.web.labportalbackend.booking.repository.BookingRepository;
import com.web.labportalbackend.booking.repository.TimeSlotRepository;
import com.web.labportalbackend.common.dto.BookingResponse;
import com.web.labportalbackend.common.dto.CreateBookingRequest;
import com.web.labportalbackend.common.enums.BookingStatus;
import com.web.labportalbackend.common.exception.SlotFullException;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
     * Create a new booking with capacity validation.
     * Flow:
     * 1. Validate user exists
     * 2. Validate time slot exists
     * 3. Check slot capacity
     * 4. Create and save booking
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

        // 3. Check slot capacity
        long currentBookingCount = bookingRepository.countByTimeSlotId(request.getSlotId());
        if (currentBookingCount >= timeSlot.getCapacity()) {
            log.warn("Slot {} is full. Current bookings: {}, Capacity: {}", 
                    request.getSlotId(), currentBookingCount, timeSlot.getCapacity());
            throw new SlotFullException(request.getSlotId(), timeSlot.getCapacity(), (int) currentBookingCount);
        }

        // 4. Create and save booking
        Booking booking = new Booking();
        booking.setUser(user);
        booking.setLab(timeSlot.getLab());
        booking.setTimeSlot(timeSlot);
        booking.setStartTime(timeSlot.getStartTime());
        booking.setEndTime(timeSlot.getEndTime());
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setParticipantsCount(1);

        Booking saved = bookingRepository.save(booking);
        log.info("Booking created successfully. Booking ID: {}, User ID: {}, Slot ID: {}", 
                saved.getId(), userId, request.getSlotId());

        return mapToResponse(saved);
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
     * Cancel a booking by marking it as CANCELLED.
     */
    @Override
    @Transactional
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
