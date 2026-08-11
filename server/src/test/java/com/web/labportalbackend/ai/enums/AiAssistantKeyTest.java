package com.web.labportalbackend.ai.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AiAssistantKeyTest {

    @Test
    void mapsEveryFixedAssistantKeyToItsOnlyCanonicalDomain() {
        assertEquals(AiAssistantDomain.ADMIN, AiAssistantKey.ADMIN_ASSISTANT.domain());
        assertEquals(AiAssistantDomain.LAB, AiAssistantKey.LAB_ASSISTANT.domain());
        assertEquals(AiAssistantDomain.RESEARCH, AiAssistantKey.RESEARCH_ASSISTANT.domain());

        assertTrue(AiAssistantKey.ADMIN_ASSISTANT.matchesDomain(AiAssistantDomain.ADMIN));
        assertFalse(AiAssistantKey.ADMIN_ASSISTANT.matchesDomain(AiAssistantDomain.LAB));
        assertFalse(AiAssistantKey.LAB_ASSISTANT.matchesDomain(AiAssistantDomain.RESEARCH));
        assertFalse(AiAssistantKey.RESEARCH_ASSISTANT.matchesDomain(null));
    }
}
