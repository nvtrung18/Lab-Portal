package com.web.labportalbackend.booking.mapper;

import com.web.labportalbackend.booking.dto.response.CleaningResponse;
import com.web.labportalbackend.booking.entity.CleaningEntity;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CleaningMapper {

    public static CleaningResponse toResponse(CleaningEntity cleaning) {
        return CleaningResponse.builder()
                .id(cleaning.getId())
                .slotId(cleaning.getSlot().getId())
                .staffId(cleaning.getStaff() != null ? cleaning.getStaff().getId() : null)
                .status(cleaning.getStatus())
                .startedAt(cleaning.getStartedAt())
                .completedAt(cleaning.getCompletedAt())
                .createdAt(cleaning.getCreatedAt())
                .build();
    }
}
