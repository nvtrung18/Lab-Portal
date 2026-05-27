package com.web.labportalbackend.booking.mapper;

import com.web.labportalbackend.booking.entity.TimeSlot;
import com.web.labportalbackend.booking.dto.response.TimeSlotResponse;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Centralized mapper for TimeSlot entity ↔ DTO conversions.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TimeSlotMapper {

    public static TimeSlotResponse toResponse(TimeSlot timeSlot) {
        return toResponse(timeSlot, null);
    }

    public static TimeSlotResponse toResponse(TimeSlot timeSlot, Long bookedCount) {
        return toResponse(timeSlot, bookedCount, 0L, 0L);
    }

    public static TimeSlotResponse toResponse(
            TimeSlot timeSlot,
            Long approvedCount,
            Long checkedInCount,
            Long pendingCount
    ) {
        long effectiveApprovedCount = approvedCount != null ? approvedCount : 0L;
        long effectiveCheckedInCount = checkedInCount != null ? checkedInCount : 0L;
        long effectivePendingCount = pendingCount != null ? pendingCount : 0L;
        long remainingCapacity = Math.max((long) timeSlot.getCapacity() - effectiveApprovedCount, 0L);

        return TimeSlotResponse.builder()
                .id(timeSlot.getId())
                .labId(timeSlot.getLab().getId())
                .startTime(timeSlot.getStartTime())
                .endTime(timeSlot.getEndTime())
                .capacity(timeSlot.getCapacity())
                .bookedCount(effectiveApprovedCount)
                .approvedCount(effectiveApprovedCount)
                .checkedInCount(effectiveCheckedInCount)
                .pendingCount(effectivePendingCount)
                .remainingCapacity(remainingCapacity)
                .status(timeSlot.getStatus())
                .createdAt(timeSlot.getCreatedAt())
                .updatedAt(timeSlot.getUpdatedAt())
                .build();
    }
}
