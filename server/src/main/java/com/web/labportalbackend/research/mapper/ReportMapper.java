package com.web.labportalbackend.research.mapper;

import com.web.labportalbackend.research.dto.response.ReportResponse;
import com.web.labportalbackend.research.entity.ReportEntity;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ReportMapper {

    public static ReportResponse toResponse(ReportEntity report) {
        return ReportResponse.builder()
                .id(report.getId())
                .taskId(report.getTaskId())
                .version(report.getVersion())
                .fileUrl(report.getFileUrl())
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getUpdatedAt())
                .build();
    }
}
