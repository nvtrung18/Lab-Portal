package com.web.labportalbackend.booking;

import com.web.labportalbackend.common.dto.BookingResponse;
import com.web.labportalbackend.common.dto.CreateBookingRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service interface for booking management.
 * Handles booking creation with capacity validation and listing.
 */
public interface BookingService {

    /**
     * Create a new booking for a user on a specific time slot.
     * Validates capacity before confirming the booking.
     *
     * @param userId the user ID
     * @param request the booking request containing slot_id
     * @return the created booking response
     * @throws jakarta.persistence.EntityNotFoundException if slot doesn't exist
     * @throws com.web.labportalbackend.common.exception.SlotFullException if slot is at capacity
     * @throws IllegalArgumentException if input validation fails
     */
    BookingResponse book(Long userId, CreateBookingRequest request);

    /**
     * Retrieve all bookings with pagination.
     *
     * @param pageable pagination parameters
     * @return paginated list of bookings
     */
    Page<BookingResponse> getAllBookings(Pageable pageable);

    /**
     * Retrieve all bookings for a specific user.
     *
     * @param userId the user ID
     * @return list of user's bookings
     */
    java.util.List<BookingResponse> getBookingsByUser(Long userId);

    /**
     * Retrieve all bookings for a specific time slot.
     *
     * @param slotId the time slot ID
     * @return list of bookings for the slot
     */
    java.util.List<BookingResponse> getBookingsBySlot(Long slotId);

    /**
     * Cancel a booking.
     *
     * @param bookingId the booking ID
     * @throws jakarta.persistence.EntityNotFoundException if booking doesn't exist
     */
    void cancelBooking(Long bookingId);
}
