package com.web.labportalbackend.booking.mapper;

import com.web.labportalbackend.booking.dto.response.WaitlistResponse;
import com.web.labportalbackend.booking.entity.WaitlistEntity;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Centralized mapper for WaitlistEntity ↔ DTO conversions.
 * Mirrors BookingMapper pattern to eliminate duplicate mapToResponse() methods.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WaitlistMapper {

    public static WaitlistResponse toResponse(WaitlistEntity waitlist) {
        return WaitlistResponse.builder()
                .id(waitlist.getId())
                .slotId(waitlist.getTimeSlot().getId())
                .userId(waitlist.getUser().getId())
                .position(waitlist.getPosition())
                .createdAt(waitlist.getCreatedAt())
                .updatedAt(waitlist.getUpdatedAt())
                .build();
    }
}
