package com.web.labportalbackend.booking.mapper;

import com.web.labportalbackend.booking.entity.Booking;
import com.web.labportalbackend.booking.dto.response.BookingResponse;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Centralized mapper for Booking entity ↔ DTO conversions.
 * Eliminates duplicate mapToResponse() methods across services.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class BookingMapper {

    public static BookingResponse toResponse(Booking booking) {
        return BookingResponse.builder()
                .id(booking.getId())
                .userId(booking.getUser().getId())
                .labId(booking.getLab().getId())
                .slotId(booking.getTimeSlot() != null ? booking.getTimeSlot().getId() : null)
                .startTime(booking.getStartTime())
                .endTime(booking.getEndTime())
                .status(booking.getStatus())
                .purpose(booking.getPurpose())
                .participantsCount(booking.getParticipantsCount())
                .createdAt(booking.getCreatedAt())
                .updatedAt(booking.getUpdatedAt())
                .build();
    }
}
