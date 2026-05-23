package com.web.labportalbackend.booking.dto.response;

import com.web.labportalbackend.common.enums.CleaningStatus;
import com.web.labportalbackend.common.enums.TimeSlotStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class CleaningResponse {
    private Long id;
    private Long slotId;
    private Long labId;
    private String labName;
    private Long staffId;
    private String staffName;
    private String staffEmail;
    private Instant startTime;
    private Instant endTime;
    private TimeSlotStatus slotStatus;
    private Long participantCount;
    private CleaningStatus status;
    private Instant startedAt;
    private Instant completedAt;
    private Instant createdAt;
}
