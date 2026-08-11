package com.web.labportalbackend.ai.enums;

public enum AiAssistantToolGroup {
    ADMIN_READ(AiAssistantDomain.ADMIN),
    ADMIN_DRAFT(AiAssistantDomain.ADMIN),
    LAB_READ(AiAssistantDomain.LAB),
    LAB_DRAFT(AiAssistantDomain.LAB),
    RESEARCH_READ(AiAssistantDomain.RESEARCH),
    RESEARCH_DRAFT(AiAssistantDomain.RESEARCH);

    private final AiAssistantDomain domain;

    AiAssistantToolGroup(AiAssistantDomain domain) {
        this.domain = domain;
    }

    public AiAssistantDomain domain() {
        return domain;
    }

    public boolean belongsTo(AiAssistantDomain candidate) {
        return domain == candidate;
    }
}
