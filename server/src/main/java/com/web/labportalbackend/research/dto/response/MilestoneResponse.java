package com.web.labportalbackend.research.dto.response;

import com.web.labportalbackend.research.enums.MilestoneStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class MilestoneResponse {
    private Long id;
    private Long projectId;
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private MilestoneStatus status;
}
