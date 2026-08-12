package com.web.labportalbackend.ai.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.web.labportalbackend.ai.entity.AiActionSuggestionEntity;
import com.web.labportalbackend.ai.enums.AiActionConfirmationStatus;
import com.web.labportalbackend.ai.enums.AiActionExecutionStatus;
import com.web.labportalbackend.ai.enums.AiActionRiskBoundary;
import com.web.labportalbackend.ai.enums.AiActionSuggestionStatus;
import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.ai.enums.AiResourceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class AiActionSuggestionRepositoryTest {

    @Autowired AiActionSuggestionRepository repository;
    @Autowired jakarta.persistence.EntityManager entityManager;

    @Test
    void legacyActionSuggestionKeepsNewAuditSnapshotsNull() {
        AiActionSuggestionEntity legacy = repository.saveAndFlush(baseSuggestion());

        entityManager.clear();

        AiActionSuggestionEntity reloaded = repository.findById(legacy.getId()).orElseThrow();
        assertEquals(AiActionSuggestionStatus.PENDING, reloaded.getStatus());
        assertNull(reloaded.getModelVersion());
        assertNull(reloaded.getAdapterVersion());
        assertNull(reloaded.getPromptVersion());
        assertNull(reloaded.getResourceType());
        assertNull(reloaded.getResourceId());
        assertNull(reloaded.getActionRiskLevel());
        assertNull(reloaded.getConfirmationStatus());
        assertNull(reloaded.getExecutionStatus());
    }

    @Test
    void trustedAuditSnapshotsRoundTripWithoutChangingLegacyFields() {
        AiActionSuggestionEntity suggestion = baseSuggestion();
        suggestion.setStatus(AiActionSuggestionStatus.EDITED);
        suggestion.setModelVersion("model-v1");
        suggestion.setAdapterVersion("adapter-v1");
        suggestion.setPromptVersion("prompt-v1");
        suggestion.setResourceType(AiResourceType.PROJECT);
        suggestion.setResourceId(90L);
        suggestion.setActionRiskLevel(AiActionRiskBoundary.CONFIRM_REQUIRED);
        suggestion.setConfirmationStatus(AiActionConfirmationStatus.CONFIRMED);
        suggestion.setExecutionStatus(AiActionExecutionStatus.NOT_REQUESTED);
        AiActionSuggestionEntity saved = repository.saveAndFlush(suggestion);

        entityManager.clear();

        AiActionSuggestionEntity reloaded = repository.findById(saved.getId()).orElseThrow();
        assertEquals("action", reloaded.getActionType());
        assertEquals("module", reloaded.getTargetModule());
        assertEquals(AiActionSuggestionStatus.EDITED, reloaded.getStatus());
        assertEquals("model-v1", reloaded.getModelVersion());
        assertEquals("adapter-v1", reloaded.getAdapterVersion());
        assertEquals("prompt-v1", reloaded.getPromptVersion());
        assertEquals(AiResourceType.PROJECT, reloaded.getResourceType());
        assertEquals(90L, reloaded.getResourceId());
        assertEquals(AiActionRiskBoundary.CONFIRM_REQUIRED, reloaded.getActionRiskLevel());
        assertEquals(AiActionConfirmationStatus.CONFIRMED, reloaded.getConfirmationStatus());
        assertEquals(AiActionExecutionStatus.NOT_REQUESTED, reloaded.getExecutionStatus());
    }

    private static AiActionSuggestionEntity baseSuggestion() {
        return AiActionSuggestionEntity.builder().requestedById(7L).assistantKey(AiAssistantKey.LAB_ASSISTANT)
                .actionType("action").targetModule("module").payloadJson("{\"source\":\"test\"}").build();
    }
}
