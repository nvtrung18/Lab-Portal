package com.web.labportalbackend.booking.service;

import com.web.labportalbackend.booking.dto.response.CheckinResponse;

/**
 * Service interface for check-in operations.
 * Handles user check-in to lab bookings with time window validation.
 */
public interface CheckinService {

    /**
     * Check in a user for a booking.
     * 
     * Validations performed:
     * 1. Booking exists and belongs to the user
     * 2. Booking status is CONFIRMED
     * 3. Current time is within check-in window:
     *    - Not before: startTime - 15 minutes
     *    - Not after: endTime
     * 
     * On success:
     * - Updates booking status to CHECKED_IN
     * - Records check-in timestamp
     * - Returns CheckinResponse with confirmation
     * 
     * @param bookingId the booking ID to check in
     * @param userId the user ID performing check-in (ownership validation)
     * @return CheckinResponse with check-in details
     * @throws EntityNotFoundException if booking not found
     * @throws AccessDeniedException if booking doesn't belong to user
     * @throws InvalidCheckinTimeException if outside time window or invalid status
     * @throws IllegalStateException if booking already checked in
     */
    CheckinResponse checkIn(Long bookingId, Long userId);
}
