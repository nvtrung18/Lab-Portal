package com.web.labportalbackend.common.enums;

/**
 * Lifecycle status for bookings.
 * 
 * Status flow:
 * PENDING → CONFIRMED → IN_PROGRESS → COMPLETED
 * PENDING → CANCELLED
 * CONFIRMED → CANCELLED
 * PENDING → WAITLISTED (when slot is full during high concurrency)
 */
public enum BookingStatus {
    PENDING,
    CONFIRMED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
    WAITLISTED  // Fallback status when booking fails due to full slot or lock timeout
}
