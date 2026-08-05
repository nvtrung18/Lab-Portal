package com.web.labportalbackend.ai.repository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.web.labportalbackend.ai.entity.AiAssistantConfigEntity;
import com.web.labportalbackend.ai.entity.AiQuotaConfigEntity;
import com.web.labportalbackend.ai.enums.AiAssistantKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class AiConfigQuotaRepositoryTest {

    @Autowired AiAssistantConfigRepository assistantConfigRepository;
    @Autowired AiQuotaConfigRepository quotaConfigRepository;

    @Test
    void assistantLookupRequiresActiveAndNonDeletedConfiguration() {
        AiAssistantConfigEntity assistant = assistant(AiAssistantKey.LAB_ASSISTANT, "assistant");
        assistantConfigRepository.saveAndFlush(assistant);

        assertTrue(assistantConfigRepository.findByAssistantKeyAndActiveTrueAndDeletedFalse(AiAssistantKey.LAB_ASSISTANT)
                .isPresent());

        assistant.setActive(false);
        assistantConfigRepository.saveAndFlush(assistant);
        assertFalse(assistantConfigRepository.findByAssistantKeyAndActiveTrueAndDeletedFalse(AiAssistantKey.LAB_ASSISTANT)
                .isPresent());

        assistant.setActive(true);
        assistant.setDeleted(true);
        assistantConfigRepository.saveAndFlush(assistant);
        assertFalse(assistantConfigRepository.findByAssistantKeyAndActiveTrueAndDeletedFalse(AiAssistantKey.LAB_ASSISTANT)
                .isPresent());
    }

    @Test
    void quotaLookupRequiresExactAssistantRoleModuleAndActiveNonDeletedRows() {
        quotaConfigRepository.saveAndFlush(quota("STUDENT", "research", true, false));
        quotaConfigRepository.saveAndFlush(quota("TEACHER", "research", false, false));
        quotaConfigRepository.saveAndFlush(quota("MANAGER", "research", true, true));

        assertTrue(quotaConfigRepository.findByAssistantKeyAndRoleAndModuleAndActiveTrueAndDeletedFalse(
                AiAssistantKey.LAB_ASSISTANT, "STUDENT", "research").isPresent());
        assertFalse(quotaConfigRepository.findByAssistantKeyAndRoleAndModuleAndActiveTrueAndDeletedFalse(
                AiAssistantKey.LAB_ASSISTANT, "STUDENT", "other").isPresent());
        assertFalse(quotaConfigRepository.findByAssistantKeyAndRoleAndModuleAndActiveTrueAndDeletedFalse(
                AiAssistantKey.LAB_ASSISTANT, "TEACHER", "research").isPresent());
        assertFalse(quotaConfigRepository.findByAssistantKeyAndRoleAndModuleAndActiveTrueAndDeletedFalse(
                AiAssistantKey.LAB_ASSISTANT, "MANAGER", "research").isPresent());
    }

    private static AiAssistantConfigEntity assistant(AiAssistantKey assistantKey, String promptKey) {
        return AiAssistantConfigEntity.builder().assistantKey(assistantKey).enabled(true).systemPromptKey(promptKey)
                .maxRequestsPerDay(10).maxContextTokens(100).build();
    }

    private static AiQuotaConfigEntity quota(String role, String module, boolean active, boolean deleted) {
        AiQuotaConfigEntity quota = AiQuotaConfigEntity.builder().assistantKey(AiAssistantKey.LAB_ASSISTANT)
                .role(role).module(module).enabled(true).maxRequestsPerDay(5).maxContextTokens(50).build();
        quota.setActive(active);
        quota.setDeleted(deleted);
        return quota;
    }
}
