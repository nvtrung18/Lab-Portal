package com.web.labportalbackend.common.exception;

/**
 * Exception thrown when attempting to create a duplicate booking.
 * A duplicate booking occurs when a user attempts to book the same time slot more than once.
 */
public class DuplicateBookingException extends RuntimeException {

    private final Long userId;
    private final Long slotId;

    public DuplicateBookingException(Long userId, Long slotId) {
        super(String.format("User %d has already booked slot %d. Duplicate bookings are not allowed.", userId, slotId));
        this.userId = userId;
        this.slotId = slotId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getSlotId() {
        return slotId;
    }
}
