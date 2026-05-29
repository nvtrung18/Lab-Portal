package com.web.labportalbackend.admin.audit.dto;

import java.util.List;

public record AuditLogPageResponse(
        List<AuditLogResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
