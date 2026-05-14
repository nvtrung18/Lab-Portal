package com.web.labportalbackend.research.port;

import com.web.labportalbackend.research.dto.ResearchAttendanceDTO;

public interface BookingStatsPort {
    ResearchAttendanceDTO getAttendanceByProject(Long projectId);
}
