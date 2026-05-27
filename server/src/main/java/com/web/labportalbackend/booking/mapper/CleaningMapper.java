package com.web.labportalbackend.booking.mapper;

import com.web.labportalbackend.booking.dto.response.CleaningResponse;
import com.web.labportalbackend.booking.entity.CleaningEntity;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CleaningMapper {

    public static CleaningResponse toResponse(CleaningEntity cleaning) {
        return toResponse(cleaning, null);
    }

    public static CleaningResponse toResponse(CleaningEntity cleaning, Long participantCount) {
        var slot = cleaning.getSlot();
        var lab = slot.getLab();
        var staff = cleaning.getStaff();

        return CleaningResponse.builder()
                .id(cleaning.getId())
                .slotId(slot.getId())
                .labId(lab.getId())
                .labName(lab.getLabName())
                .staffId(staff != null ? staff.getId() : null)
                .staffName(staff != null ? staff.getFullName() : null)
                .staffEmail(staff != null ? staff.getEmail() : null)
                .startTime(slot.getStartTime())
                .endTime(slot.getEndTime())
                .slotStatus(slot.getStatus())
                .participantCount(participantCount)
                .status(cleaning.getStatus())
                .startedAt(cleaning.getStartedAt())
                .completedAt(cleaning.getCompletedAt())
                .createdAt(cleaning.getCreatedAt())
                .build();
    }
}
