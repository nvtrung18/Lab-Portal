package com.web.labportalbackend.booking.mapper;

import com.web.labportalbackend.booking.dto.response.PenaltyResponse;
import com.web.labportalbackend.booking.entity.PenaltyEntity;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PenaltyMapper {

    public static PenaltyResponse toResponse(PenaltyEntity penalty) {
        var booking = penalty.getBooking();
        var lab = penalty.getLab() != null ? penalty.getLab() : booking.getLab();
        var slot = penalty.getSlot() != null ? penalty.getSlot() : booking.getTimeSlot();

        return PenaltyResponse.builder()
                .id(penalty.getId())
                .userId(penalty.getUser().getId())
                .labId(lab.getId())
                .labName(lab.getLabName())
                .bookingId(booking != null ? booking.getId() : null)
                .slotId(slot != null ? slot.getId() : null)
                .createdById(penalty.getCreatedBy() != null ? penalty.getCreatedBy().getId() : null)
                .createdByName(penalty.getCreatedBy() != null ? penalty.getCreatedBy().getFullName() : null)
                .type(penalty.getType())
                .reason(penalty.getReason())
                .point(penalty.getPoint())
                .amount(penalty.getAmount())
                .status(penalty.getStatus())
                .createdAt(penalty.getCreatedAt())
                .complaint(penalty.getComplaint() != null
                        ? ComplaintMapper.toResponse(penalty.getComplaint())
                        : null)
                .build();
    }
}
