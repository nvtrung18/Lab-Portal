package com.web.labportalbackend.admin.audit.dto;

import com.web.labportalbackend.admin.audit.enums.AuditAction;
import com.web.labportalbackend.admin.audit.enums.AuditModule;

import java.time.Instant;

public record AuditLogFilter(
        Long actorId,
        AuditAction action,
        AuditModule module,
        Instant fromDate,
        Instant toDate,
        String keyword
) {
}
