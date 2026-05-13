package com.web.labportalbackend.booking.dto.response;

import com.web.labportalbackend.common.enums.CleaningStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class CleaningResponse {
    private Long id;
    private Long slotId;
    private Long staffId;
    private CleaningStatus status;
    private Instant startedAt;
    private Instant completedAt;
    private Instant createdAt;
}
