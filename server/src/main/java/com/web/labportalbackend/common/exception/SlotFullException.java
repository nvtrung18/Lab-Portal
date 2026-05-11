package com.web.labportalbackend.common.exception;

/**
 * Exception thrown when attempting to book a time slot that has reached capacity.
 */
public class SlotFullException extends RuntimeException {

    public SlotFullException(String message) {
        super(message);
    }

    public SlotFullException(String message, Throwable cause) {
        super(message, cause);
    }

    public SlotFullException(Long slotId, int capacity, int currentBookings) {
        super(String.format("Slot %d is full. Capacity: %d, Current bookings: %d", slotId, capacity, currentBookings));
    }
}
