package com.web.labportalbackend.ai.enums;

public enum AiAssistantKey {
    ADMIN_ASSISTANT(AiAssistantDomain.ADMIN),
    LAB_ASSISTANT(AiAssistantDomain.LAB),
    RESEARCH_ASSISTANT(AiAssistantDomain.RESEARCH);

    private final AiAssistantDomain domain;

    AiAssistantKey(AiAssistantDomain domain) {
        this.domain = domain;
    }

    public AiAssistantDomain domain() {
        return domain;
    }

    public boolean matchesDomain(AiAssistantDomain candidate) {
        return domain == candidate;
    }
}
