package com.web.labportalbackend.research.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ResearchAttendanceDTO {

    @JsonProperty("total_bookings")
    private long totalBookings;

    @JsonProperty("attended_sessions")
    private long attendedSessions;

    @JsonProperty("no_show_sessions")
    private long noShowSessions;
}
