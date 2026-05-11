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
        return TimeSlotResponse.builder()
                .id(timeSlot.getId())
                .labId(timeSlot.getLab().getId())
                .startTime(timeSlot.getStartTime())
                .endTime(timeSlot.getEndTime())
                .capacity(timeSlot.getCapacity())
                .status(timeSlot.getStatus())
                .createdAt(timeSlot.getCreatedAt())
                .updatedAt(timeSlot.getUpdatedAt())
                .build();
    }
}
