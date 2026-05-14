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
    private String content;
    private ComplaintStatus status;
    private Instant createdAt;
}
