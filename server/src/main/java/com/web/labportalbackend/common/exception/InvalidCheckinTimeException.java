package com.web.labportalbackend.common.exception;

/**
 * Exception thrown when check-in occurs outside the allowed time window.
 * Provides detailed reason for rejection: TOO_EARLY, TOO_LATE, or INVALID_STATUS.
 */
public class InvalidCheckinTimeException extends RuntimeException {

    public enum Reason {
        TOO_EARLY("Check-in window not yet open. Please arrive closer to the start time."),
        TOO_LATE("Check-in window has closed. You are too late for this booking."),
        INVALID_STATUS("Booking is not in CONFIRMED status and cannot be checked in.");

        private final String message;

        Reason(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    private final Reason reason;
    private final long bookingId;

    public InvalidCheckinTimeException(long bookingId, Reason reason) {
        super(String.format("[Booking %d] %s", bookingId, reason.getMessage()));
        this.bookingId = bookingId;
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    public long getBookingId() {
        return bookingId;
    }
}
