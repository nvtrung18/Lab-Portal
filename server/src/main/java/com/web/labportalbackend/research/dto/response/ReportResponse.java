package com.web.labportalbackend.research.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class ReportResponse {
    private Long id;
    private Long taskId;
    private Integer version;
    private String fileUrl;
    private Instant createdAt;
    private Instant updatedAt;
}
