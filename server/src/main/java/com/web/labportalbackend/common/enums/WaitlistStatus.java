package com.web.labportalbackend.common.enums;

/**
 * Status for waitlist entries.
 * <p>
 * Lifecycle:
 * PENDING → PROMOTED (when user is moved from waitlist to booking)
 * PENDING → CANCELLED (if user or slot is deleted)
 * <p>
 * By tracking status instead of hard-deleting, we maintain an audit trail
 * of which users were promoted and when.
 */
public enum WaitlistStatus {
    /**
     * User is in the queue waiting for slot to become available.
     */
    PENDING,

    /**
     * User has been promoted to a confirmed booking.
     * Kept as historical record (soft delete).
     */
    PROMOTED,

    /**
     * Waitlist entry was cancelled (user cancelled, slot deleted, etc).
     * Kept as historical record (soft delete).
     */
    CANCELLED
}
