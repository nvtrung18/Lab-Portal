package com.web.labportalbackend.research.mapper;

import com.web.labportalbackend.research.dto.response.MilestoneResponse;
import com.web.labportalbackend.research.entity.MilestoneEntity;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MilestoneMapper {

    public static MilestoneResponse toResponse(MilestoneEntity milestone) {
        return MilestoneResponse.builder()
                .id(milestone.getId())
                .projectId(milestone.getProject().getId())
                .name(milestone.getName())
                .startDate(milestone.getStartDate())
                .endDate(milestone.getEndDate())
                .status(milestone.getStatus())
                .build();
    }
}
