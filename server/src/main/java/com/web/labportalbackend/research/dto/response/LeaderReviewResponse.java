package com.web.labportalbackend.research.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LeaderReviewResponse {
    private String message;
    private ReportSummary report;

    @Getter
    @Builder
    public static class ReportSummary {
        private Long id;
        private String status;
        private Integer version;
    }
}
