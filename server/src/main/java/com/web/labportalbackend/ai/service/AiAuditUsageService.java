package com.web.labportalbackend.ai.service;

public interface AiAuditUsageService {

    void recordAssistantRequest(AiAssistantAuditEvent event);

    void recordToolOutcome(AiToolAuditEvent event);
}
