package com.web.labportalbackend.admin.operations.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record OperationalLogPageResponse<T>(
        @Schema(description = "Operational log entries in the requested page") List<T> items,
        @Schema(description = "Zero-based page number") int page,
        @Schema(description = "Maximum entries in the page") int size,
        @Schema(description = "Total matching operational log entries") long totalElements,
        @Schema(description = "Total matching pages") int totalPages
) {
}
