package com.web.labportalbackend.admin.audit.service;

import com.web.labportalbackend.admin.audit.dto.AuditLogFilter;
import com.web.labportalbackend.admin.audit.dto.AuditLogPageResponse;
import com.web.labportalbackend.admin.audit.enums.AuditAction;
import com.web.labportalbackend.admin.audit.enums.AuditModule;
import com.web.labportalbackend.auth.entity.User;
import org.springframework.data.domain.Pageable;

public interface AuditLogService {

    void log(
            User actor,
            AuditAction action,
            AuditModule module,
            String targetType,
            Long targetId,
            String description
    );

    void log(
            User actor,
            AuditAction action,
            AuditModule module,
            String targetType,
            Long targetId,
            String description,
            String metadataJson
    );

    void logCurrentUser(
            AuditAction action,
            AuditModule module,
            String targetType,
            Long targetId,
            String description
    );

    AuditLogPageResponse getAuditLogs(AuditLogFilter filter, Pageable pageable);
}
