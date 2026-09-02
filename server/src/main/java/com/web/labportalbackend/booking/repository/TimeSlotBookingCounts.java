package com.web.labportalbackend.booking.repository;

public record TimeSlotBookingCounts(
        Long timeSlotId,
        Long approvedCount,
        Long checkedInCount,
        Long pendingCount
) {
}
