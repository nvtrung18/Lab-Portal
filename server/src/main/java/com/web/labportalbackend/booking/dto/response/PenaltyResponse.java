package com.web.labportalbackend.booking.dto.response;

import com.web.labportalbackend.common.enums.PenaltyStatus;
import com.web.labportalbackend.common.enums.PenaltyType;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
public class PenaltyResponse {
    private Long id;
    private Long userId;
    private Long labId;
    private String labName;
    private Long bookingId;
    private Long slotId;
    private Long createdById;
    private String createdByName;
    private PenaltyType type;
    private String reason;
    private Integer point;
    private BigDecimal amount;
    private PenaltyStatus status;
    private Instant createdAt;
    private ComplaintResponse complaint;
}
