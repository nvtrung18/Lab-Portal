package com.web.labportalbackend.booking.mapper;

import com.web.labportalbackend.booking.dto.response.PenaltyResponse;
import com.web.labportalbackend.booking.entity.PenaltyEntity;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PenaltyMapper {

    public static PenaltyResponse toResponse(PenaltyEntity penalty) {
        return PenaltyResponse.builder()
                .id(penalty.getId())
                .userId(penalty.getUser().getId())
                .bookingId(penalty.getBooking().getId())
                .reason(penalty.getReason())
                .amount(penalty.getAmount())
                .status(penalty.getStatus())
                .createdAt(penalty.getCreatedAt())
                .build();
    }
}
