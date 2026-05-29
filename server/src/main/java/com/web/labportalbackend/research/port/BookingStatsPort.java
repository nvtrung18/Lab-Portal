package com.web.labportalbackend.research.port;

import com.web.labportalbackend.research.dto.ResearchAttendanceDTO;
import com.web.labportalbackend.research.dto.StudentAttendanceDTO;

import java.util.List;

public interface BookingStatsPort {
    ResearchAttendanceDTO getAttendanceByProject(Long projectId);

    ResearchAttendanceDTO getAttendanceByGroup(Long projectId, Long groupId);

    ResearchAttendanceDTO getAttendanceByStudent(Long projectId, Long studentId);

    List<StudentAttendanceDTO> getAttendanceByStudents(Long projectId);

    List<StudentAttendanceDTO> getAttendanceByStudents(Long projectId, Long groupId);
}
