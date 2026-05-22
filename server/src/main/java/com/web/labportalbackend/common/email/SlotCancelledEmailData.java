package com.web.labportalbackend.common.email;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class SlotCancelledEmailData {
    private String labName;
    private Instant startTime;
    private Instant endTime;
    private String reason;
    private String managerName;
}
