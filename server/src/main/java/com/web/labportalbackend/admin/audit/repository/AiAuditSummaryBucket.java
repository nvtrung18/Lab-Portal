package com.web.labportalbackend.admin.audit.repository;

/** Server-side grouped projection that never materializes raw audit text or JSON. */
public interface AiAuditSummaryBucket {
    Object getDay();
    com.web.labportalbackend.admin.audit.enums.AuditModule getModule();
    com.web.labportalbackend.admin.audit.enums.AuditAction getAction();
    long getCount();
}
