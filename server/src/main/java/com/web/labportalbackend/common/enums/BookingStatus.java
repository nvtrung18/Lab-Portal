package com.web.labportalbackend.common.enums;

/**
 * Lifecycle status for bookings.
 * 
 * Status flow:
 * PENDING → CONFIRMED → CHECKED_IN → IN_PROGRESS → COMPLETED
 * PENDING → CANCELLED
 * CONFIRMED → CANCELLED
 * CONFIRMED → CHECKED_IN (when user checks in)
 * PENDING → WAITLISTED (when slot is full during high concurrency)
 */
public enum BookingStatus {
    PENDING,
    CONFIRMED,
    CHECKED_IN,
    IN_PROGRESS,
    COMPLETED,
    NO_SHOW,
    CANCELLED,
    WAITLISTED  // Fallback status when booking fails due to full slot or lock timeout
}
