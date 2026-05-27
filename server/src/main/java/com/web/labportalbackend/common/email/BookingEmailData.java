package com.web.labportalbackend.common.email;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class BookingEmailData {
    private String studentName;
    private String labName;
    private Instant startTime;
    private Instant endTime;
    private String status;
    private String note;
}
