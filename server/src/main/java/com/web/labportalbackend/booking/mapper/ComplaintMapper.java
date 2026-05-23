package com.web.labportalbackend.booking.mapper;

import com.web.labportalbackend.booking.dto.response.ComplaintResponse;
import com.web.labportalbackend.booking.entity.ComplaintEntity;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ComplaintMapper {

    public static ComplaintResponse toResponse(ComplaintEntity complaint) {
        var penalty = complaint.getPenalty();
        var booking = penalty != null ? penalty.getBooking() : null;
        var lab = penalty != null && penalty.getLab() != null ? penalty.getLab() : booking != null ? booking.getLab() : null;

        return ComplaintResponse.builder()
                .id(complaint.getId())
                .userId(complaint.getUser().getId())
                .studentName(complaint.getUser().getFullName())
                .studentEmail(complaint.getUser().getEmail())
                .penaltyId(complaint.getPenalty() != null ? complaint.getPenalty().getId() : null)
                .labId(lab != null ? lab.getId() : null)
                .labName(lab != null ? lab.getLabName() : null)
                .bookingId(booking != null ? booking.getId() : null)
                .penaltyReason(penalty != null ? penalty.getReason() : null)
                .penalty(null)
                .content(complaint.getContent())
                .status(complaint.getStatus())
                .resolutionNote(complaint.getResolutionNote())
                .resolvedAt(complaint.getResolvedAt())
                .createdAt(complaint.getCreatedAt())
                .build();
    }
}
