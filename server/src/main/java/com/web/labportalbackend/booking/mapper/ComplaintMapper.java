package com.web.labportalbackend.booking.mapper;

import com.web.labportalbackend.booking.dto.response.ComplaintResponse;
import com.web.labportalbackend.booking.entity.ComplaintEntity;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ComplaintMapper {

    public static ComplaintResponse toResponse(ComplaintEntity complaint) {
        return ComplaintResponse.builder()
                .id(complaint.getId())
                .userId(complaint.getUser().getId())
                .content(complaint.getContent())
                .status(complaint.getStatus())
                .createdAt(complaint.getCreatedAt())
                .build();
    }
}
