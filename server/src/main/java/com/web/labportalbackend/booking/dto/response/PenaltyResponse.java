package com.web.labportalbackend.booking.dto.response;

import com.web.labportalbackend.common.enums.PenaltyStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
public class PenaltyResponse {
    private Long id;
    private Long userId;
    private Long bookingId;
    private String reason;
    private BigDecimal amount;
    private PenaltyStatus status;
    private Instant createdAt;
}
