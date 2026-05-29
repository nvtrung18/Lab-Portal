package com.web.labportalbackend.lab.dto.response;

import com.web.labportalbackend.research.dto.StudentAttendanceDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
@Schema(description = "Laboratory dashboard statistics response")
public class LabDashboardStatsResponse {
    @Schema(description = "Laboratory ID")
    private Long labId;

    @Schema(description = "Total active members count")
    private long memberCount;

    @Schema(description = "Number of active time slots starting today")
    private long todaySlots;

    @Schema(description = "Number of active bookings starting today")
    private long todayBookings;

    @Schema(description = "Overall lab attendance rate (percentage)")
    private double attendanceRate;

    @Schema(description = "Student members attendance statistics list")
    private List<StudentAttendanceDTO> attendanceByStudent;

    @Schema(description = "Number of pending/assigned incomplete cleaning tasks")
    private long pendingCleaningTasks;

    @Schema(description = "Number of pending complaints")
    private long pendingComplaints;

    @Schema(description = "Number of active research projects in this laboratory")
    private long activeResearchProjects;
}
