package com.web.labportalbackend.common.enums;

/**
 * Represents the status of a time slot.
 */
public enum TimeSlotStatus {
    /**
     * Slot is available for booking
     */
    AVAILABLE,

    /**
     * Slot is fully booked (capacity reached)
     */
    FULL,

    CLOSED,
    INACTIVE,
    MAINTENANCE,
    EXPIRED,
    CANCELLED,
    ARCHIVED
}
