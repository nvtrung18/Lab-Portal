package com.web.labportalbackend.booking.dto.response;

import com.web.labportalbackend.common.enums.ComplaintStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class ComplaintResponse {
    private Long id;
    private Long userId;
    private String studentName;
    private String studentEmail;
    private Long penaltyId;
    private Long labId;
    private String labName;
    private Long bookingId;
    private String penaltyReason;
    private PenaltyResponse penalty;
    private String content;
    private ComplaintStatus status;
    private String resolutionNote;
    private Instant resolvedAt;
    private Instant createdAt;
}
