package com.web.labportalbackend.research.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StudentAttendanceDTO {

    private Long studentId;

    private String studentName;

    private long attendanceCount;

    private long expectedAttendanceCount;

    private double attendanceRate;
}
