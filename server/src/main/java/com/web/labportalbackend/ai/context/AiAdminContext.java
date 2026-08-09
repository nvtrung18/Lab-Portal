package com.web.labportalbackend.ai.context;

import com.web.labportalbackend.common.enums.UserStatus;
import com.web.labportalbackend.admin.audit.enums.AuditAction;
import com.web.labportalbackend.admin.audit.enums.AuditModule;
import java.time.LocalDate;

public record AiAdminContext(
        SystemSummary systemSummary,
        TargetUser targetUser,
        AiBoundedList<AuditBucket> auditBuckets,
        boolean draftOnly) implements AiDomainContext {

    public record SystemSummary(long activeUserCount, long registeredUserCount) {
    }

    public record TargetUser(Long id, UserStatus status, boolean active) {
    }

    /** No raw audit description, metadata, actor, target, IP, or user-agent fields. */
    public record AuditBucket(LocalDate day, AuditModule module, AuditAction action, long count) {
    }
}
