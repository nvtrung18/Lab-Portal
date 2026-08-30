package com.web.labportalbackend.ai.rag.enums;

import com.web.labportalbackend.ai.enums.AiAssistantDomain;

public enum AiKnowledgeNamespace {
    ADMIN_KNOWLEDGE("admin-knowledge", AiAssistantDomain.ADMIN),
    LAB_KNOWLEDGE("lab-knowledge", AiAssistantDomain.LAB),
    RESEARCH_KNOWLEDGE("research-knowledge", AiAssistantDomain.RESEARCH);

    private final String value;
    private final AiAssistantDomain domain;

    AiKnowledgeNamespace(String value, AiAssistantDomain domain) {
        this.value = value;
        this.domain = domain;
    }

    public String value() {
        return value;
    }

    public AiAssistantDomain domain() {
        return domain;
    }

    public static AiKnowledgeNamespace forDomain(AiAssistantDomain domain) {
        if (domain == null) {
            throw new IllegalArgumentException("RAG domain is required");
        }
        return switch (domain) {
            case ADMIN -> ADMIN_KNOWLEDGE;
            case LAB -> LAB_KNOWLEDGE;
            case RESEARCH -> RESEARCH_KNOWLEDGE;
        };
    }
}
