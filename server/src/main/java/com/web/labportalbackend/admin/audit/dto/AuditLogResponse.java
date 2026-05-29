package com.web.labportalbackend.admin.audit.dto;

import com.web.labportalbackend.admin.audit.enums.AuditAction;
import com.web.labportalbackend.admin.audit.enums.AuditModule;

import java.time.Instant;

public record AuditLogResponse(
        Long id,
        Long actorId,
        String actorName,
        String actorRole,
        AuditAction action,
        AuditModule module,
        String targetType,
        Long targetId,
        String description,
        String metadataJson,
        Instant createdAt
) {
}
