package com.web.labportalbackend.research.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ResearchAttendanceDTO {

    private long totalBookings;

    private long attendedSessions;

    private long noShowSessions;

    private double attendanceRate;
}
