package com.web.labportalbackend.ai.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.web.labportalbackend.ai.entity.AiAssistantConfigEntity;
import com.web.labportalbackend.ai.entity.AiQuotaConfigEntity;
import com.web.labportalbackend.ai.entity.AiUsageLogEntity;
import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.ai.repository.AiAssistantConfigRepository;
import com.web.labportalbackend.ai.repository.AiQuotaConfigRepository;
import com.web.labportalbackend.ai.repository.AiUsageLogRepository;
import com.web.labportalbackend.ai.service.AiConfigQuotaService;
import com.web.labportalbackend.ai.service.AiQuotaCheckRequest;
import com.web.labportalbackend.ai.service.AiQuotaDecision;
import com.web.labportalbackend.ai.service.AiQuotaDenialReason;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiConfigQuotaServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-08-05T14:30:00Z");

    @Mock AiAssistantConfigRepository assistantConfigRepository;
    @Mock AiQuotaConfigRepository quotaConfigRepository;
    @Mock AiUsageLogRepository usageLogRepository;

    private AiConfigQuotaService service;

    @BeforeEach
    void setUp() {
        service = new AiConfigQuotaServiceImpl(assistantConfigRepository, quotaConfigRepository, usageLogRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void unavailableAssistantConfigurationDeniesWithoutQuotaOrUsageLookups() {
        when(assistantConfigRepository.findByAssistantKeyAndActiveTrueAndDeletedFalse(AiAssistantKey.LAB_ASSISTANT))
                .thenReturn(Optional.empty());

        AiQuotaDecision decision = service.evaluate(request(1));

        assertDenied(decision, AiQuotaDenialReason.ASSISTANT_CONFIGURATION_UNAVAILABLE);
        verifyNoInteractions(quotaConfigRepository, usageLogRepository);
    }

    @Test
    void disabledAssistantConfigurationDeniesBeforeQuotaOrUsageLookups() {
        AiAssistantConfigEntity assistant = assistantConfig(true, 5, 100);
        assistant.setEnabled(false);
        when(assistantConfigRepository.findByAssistantKeyAndActiveTrueAndDeletedFalse(AiAssistantKey.LAB_ASSISTANT))
                .thenReturn(Optional.of(assistant));

        AiQuotaDecision decision = service.evaluate(request(1));

        assertDenied(decision, AiQuotaDenialReason.ASSISTANT_DISABLED);
        verifyNoInteractions(quotaConfigRepository, usageLogRepository);
    }

    @Test
    void nullEnabledAssistantConfigurationDeniesBeforeQuotaOrUsageLookups() {
        AiAssistantConfigEntity assistant = assistantConfig(true, 5, 100);
        assistant.setEnabled(null);
        when(assistantConfigRepository.findByAssistantKeyAndActiveTrueAndDeletedFalse(AiAssistantKey.LAB_ASSISTANT))
                .thenReturn(Optional.of(assistant));

        AiQuotaDecision decision = service.evaluate(request(1));

        assertDenied(decision, AiQuotaDenialReason.ASSISTANT_DISABLED);
        verifyNoInteractions(quotaConfigRepository, usageLogRepository);
    }

    @Test
    void malformedRequiredAssistantMetadataFailsClosedBeforeQuotaOrUsageLookups() {
        AiAssistantConfigEntity assistant = assistantConfig(true, 5, 100);
        assistant.setSystemPromptKey(" ");
        when(assistantConfigRepository.findByAssistantKeyAndActiveTrueAndDeletedFalse(AiAssistantKey.LAB_ASSISTANT))
                .thenReturn(Optional.of(assistant));

        AiQuotaDecision decision = service.evaluate(request(1));

        assertDenied(decision, AiQuotaDenialReason.ASSISTANT_CONFIGURATION_UNAVAILABLE);
        verifyNoInteractions(quotaConfigRepository, usageLogRepository);
    }

    @Test
    void malformedRequiredAssistantLimitsFailClosedBeforeQuotaOrUsageLookups() {
        AiAssistantConfigEntity assistant = assistantConfig(true, 5, 100);
        assistant.setMaxRequestsPerDay(null);
        when(assistantConfigRepository.findByAssistantKeyAndActiveTrueAndDeletedFalse(AiAssistantKey.LAB_ASSISTANT))
                .thenReturn(Optional.of(assistant));

        AiQuotaDecision decision = service.evaluate(request(1));

        assertDenied(decision, AiQuotaDenialReason.ASSISTANT_CONFIGURATION_UNAVAILABLE);
        verifyNoInteractions(quotaConfigRepository, usageLogRepository);
    }

    @Test
    void disabledNarrowQuotaDeniesBeforeUsageCounts() {
        stubAssistant(5, 100);
        AiQuotaConfigEntity quota = quotaConfig(3, 80);
        quota.setEnabled(false);
        stubQuota("STUDENT", "research", quota);

        AiQuotaDecision decision = service.evaluate(request(1));

        assertDenied(decision, AiQuotaDenialReason.QUOTA_DISABLED);
        verifyNoInteractions(usageLogRepository);
    }

    @Test
    void malformedPresentNarrowQuotaFailsClosedBeforeUsageCounts() {
        stubAssistant(5, 100);
        AiQuotaConfigEntity quota = quotaConfig(3, 80);
        quota.setMaxContextTokens(null);
        stubQuota("STUDENT", "research", quota);

        AiQuotaDecision decision = service.evaluate(request(1));

        assertDenied(decision, AiQuotaDenialReason.ASSISTANT_CONFIGURATION_UNAVAILABLE);
        verifyNoInteractions(usageLogRepository);
    }

    @Test
    void missingOptionalNarrowQuotaUsesValidAssistantWideLimits() {
        stubAssistant(5, 100);
        when(quotaConfigRepository.findByAssistantKeyAndRoleAndModuleAndActiveTrueAndDeletedFalse(
                AiAssistantKey.LAB_ASSISTANT, "STUDENT", "research")).thenReturn(Optional.empty());
        stubAssistantUsageCount(0);

        AiQuotaDecision decision = service.evaluate(request(1));

        assertEquals(AiQuotaDecision.allow(), decision);
        verify(usageLogRepository, never())
                .countByUserIdAndAssistantKeyAndRoleAndModuleAndCreatedAtGreaterThanEqualAndCreatedAtLessThanAndActiveTrueAndDeletedFalse(
                        anyLong(), any(), any(), any(), any(), any());
        verify(usageLogRepository, never()).save(any(AiUsageLogEntity.class));
    }

    @Test
    void normalizesRoleAndModuleBeforeLookingUpNarrowQuota() {
        stubAssistant(5, 100);
        stubQuota("STUDENT", "research", quotaConfig(3, 80));
        stubAssistantUsageCount(0);
        stubNarrowUsageCount(0);

        AiQuotaDecision decision = service.evaluate(new AiQuotaCheckRequest(7L, AiAssistantKey.LAB_ASSISTANT,
                "  ROLE_student ", "  RESEARCH  ", 40));

        assertEquals(AiQuotaDecision.allow(), decision);
        verify(quotaConfigRepository).findByAssistantKeyAndRoleAndModuleAndActiveTrueAndDeletedFalse(
                AiAssistantKey.LAB_ASSISTANT, "STUDENT", "research");
    }

    @Test
    void assistantWideUsageCountBlocksAcrossRolesAndModulesBeforeNarrowCount() {
        stubAssistant(2, 100);
        stubQuota("STUDENT", "research", quotaConfig(1, 80));
        stubAssistantUsageCount(2);

        AiQuotaDecision decision = service.evaluate(request(1));

        assertDenied(decision, AiQuotaDenialReason.ASSISTANT_DAILY_LIMIT_REACHED);
        verify(usageLogRepository).countByUserIdAndAssistantKeyAndCreatedAtGreaterThanEqualAndCreatedAtLessThanAndActiveTrueAndDeletedFalse(
                7L, AiAssistantKey.LAB_ASSISTANT, Instant.parse("2026-08-05T00:00:00Z"),
                Instant.parse("2026-08-06T00:00:00Z"));
        verify(usageLogRepository, never()).countByUserIdAndAssistantKeyAndRoleAndModuleAndCreatedAtGreaterThanEqualAndCreatedAtLessThanAndActiveTrueAndDeletedFalse(
                anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void narrowUsageCountBlocksOnlyAfterAssistantWideLimitAllows() {
        stubAssistant(5, 100);
        stubQuota("STUDENT", "research", quotaConfig(2, 80));
        stubAssistantUsageCount(1);
        stubNarrowUsageCount(2);

        AiQuotaDecision decision = service.evaluate(request(1));

        assertDenied(decision, AiQuotaDenialReason.QUOTA_DAILY_LIMIT_REACHED);
    }

    @Test
    void narrowerContextLimitDeniesBeforeUsageCounts() {
        stubAssistant(5, 100);
        stubQuota("STUDENT", "research", quotaConfig(3, 40));

        AiQuotaDecision decision = service.evaluate(request(41));

        assertDenied(decision, AiQuotaDenialReason.CONTEXT_LIMIT_EXCEEDED);
        verifyNoInteractions(usageLogRepository);
    }

    @Test
    void invalidRequestValuesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new AiQuotaCheckRequest(null, AiAssistantKey.LAB_ASSISTANT, "STUDENT", "research", 1));
        assertThrows(IllegalArgumentException.class,
                () -> new AiQuotaCheckRequest(1L, AiAssistantKey.LAB_ASSISTANT, " ", "research", 1));
        assertThrows(IllegalArgumentException.class,
                () -> new AiQuotaCheckRequest(1L, AiAssistantKey.LAB_ASSISTANT, "STUDENT", "research", -1));
    }

    private AiQuotaCheckRequest request(int contextTokens) {
        return new AiQuotaCheckRequest(7L, AiAssistantKey.LAB_ASSISTANT, "STUDENT", "research", contextTokens);
    }

    private void stubAssistant(int maxRequests, int maxContextTokens) {
        when(assistantConfigRepository.findByAssistantKeyAndActiveTrueAndDeletedFalse(AiAssistantKey.LAB_ASSISTANT))
                .thenReturn(Optional.of(assistantConfig(true, maxRequests, maxContextTokens)));
    }

    private void stubQuota(String role, String module, AiQuotaConfigEntity quota) {
        when(quotaConfigRepository.findByAssistantKeyAndRoleAndModuleAndActiveTrueAndDeletedFalse(
                AiAssistantKey.LAB_ASSISTANT, role, module)).thenReturn(Optional.of(quota));
    }

    private void stubAssistantUsageCount(long count) {
        when(usageLogRepository.countByUserIdAndAssistantKeyAndCreatedAtGreaterThanEqualAndCreatedAtLessThanAndActiveTrueAndDeletedFalse(
                anyLong(), any(), any(), any())).thenReturn(count);
    }

    private void stubNarrowUsageCount(long count) {
        when(usageLogRepository.countByUserIdAndAssistantKeyAndRoleAndModuleAndCreatedAtGreaterThanEqualAndCreatedAtLessThanAndActiveTrueAndDeletedFalse(
                anyLong(), any(), any(), any(), any(), any())).thenReturn(count);
    }

    private static AiAssistantConfigEntity assistantConfig(boolean enabled, int maxRequests, int maxContextTokens) {
        return AiAssistantConfigEntity.builder().assistantKey(AiAssistantKey.LAB_ASSISTANT).enabled(enabled)
                .systemPromptKey("test").maxRequestsPerDay(maxRequests).maxContextTokens(maxContextTokens).build();
    }

    private static AiQuotaConfigEntity quotaConfig(int maxRequests, int maxContextTokens) {
        return AiQuotaConfigEntity.builder().assistantKey(AiAssistantKey.LAB_ASSISTANT).role("STUDENT")
                .module("research").enabled(true).maxRequestsPerDay(maxRequests).maxContextTokens(maxContextTokens).build();
    }

    private static void assertDenied(AiQuotaDecision decision, AiQuotaDenialReason reason) {
        assertFalse(decision.allowed());
        assertEquals(reason, decision.denialReason());
    }
}
