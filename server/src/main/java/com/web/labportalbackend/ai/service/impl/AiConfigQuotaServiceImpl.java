package com.web.labportalbackend.ai.service.impl;

import com.web.labportalbackend.ai.entity.AiAssistantConfigEntity;
import com.web.labportalbackend.ai.entity.AiQuotaConfigEntity;
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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiConfigQuotaServiceImpl implements AiConfigQuotaService {

    private final AiAssistantConfigRepository assistantConfigRepository;
    private final AiQuotaConfigRepository quotaConfigRepository;
    private final AiUsageLogRepository usageLogRepository;
    private final Clock clock;

    @Autowired
    public AiConfigQuotaServiceImpl(AiAssistantConfigRepository assistantConfigRepository,
                                    AiQuotaConfigRepository quotaConfigRepository,
                                    AiUsageLogRepository usageLogRepository) {
        this(assistantConfigRepository, quotaConfigRepository, usageLogRepository, Clock.systemUTC());
    }

    AiConfigQuotaServiceImpl(AiAssistantConfigRepository assistantConfigRepository,
                             AiQuotaConfigRepository quotaConfigRepository,
                             AiUsageLogRepository usageLogRepository, Clock clock) {
        this.assistantConfigRepository = assistantConfigRepository;
        this.quotaConfigRepository = quotaConfigRepository;
        this.usageLogRepository = usageLogRepository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public AiQuotaDecision evaluate(AiQuotaCheckRequest request) {
        String role = normalizeRole(request.role());
        String module = normalizeModule(request.module());
        Optional<AiAssistantConfigEntity> assistantConfig = assistantConfigRepository
                .findByAssistantKeyAndActiveTrueAndDeletedFalse(request.assistantKey());
        if (assistantConfig.isEmpty()) {
            return AiQuotaDecision.deny(AiQuotaDenialReason.ASSISTANT_CONFIGURATION_UNAVAILABLE);
        }
        AiAssistantConfigEntity assistant = assistantConfig.get();
        if (assistant.getAssistantKey() != request.assistantKey()) {
            return AiQuotaDecision.deny(AiQuotaDenialReason.ASSISTANT_CONFIGURATION_UNAVAILABLE);
        }
        if (!Boolean.TRUE.equals(assistant.getEnabled())) {
            return AiQuotaDecision.deny(AiQuotaDenialReason.ASSISTANT_DISABLED);
        }
        if (!hasText(assistant.getSystemPromptKey()) || !positive(assistant.getMaxRequestsPerDay())
                || !positive(assistant.getMaxContextTokens())) {
            return AiQuotaDecision.deny(AiQuotaDenialReason.ASSISTANT_CONFIGURATION_UNAVAILABLE);
        }

        Optional<AiQuotaConfigEntity> quotaConfig = quotaConfigRepository
                .findByAssistantKeyAndRoleAndModuleAndActiveTrueAndDeletedFalse(request.assistantKey(), role, module);
        if (quotaConfig.isPresent() && !Boolean.TRUE.equals(quotaConfig.get().getEnabled())) {
            return AiQuotaDecision.deny(AiQuotaDenialReason.QUOTA_DISABLED);
        }
        if (quotaConfig.isPresent() && !validQuotaConfig(quotaConfig.get(), request.assistantKey())) {
            return AiQuotaDecision.deny(AiQuotaDenialReason.ASSISTANT_CONFIGURATION_UNAVAILABLE);
        }

        int contextLimit = quotaConfig.map(quota -> Math.min(assistant.getMaxContextTokens(), quota.getMaxContextTokens()))
                .orElse(assistant.getMaxContextTokens());
        if (request.contextTokens() > contextLimit) {
            return AiQuotaDecision.deny(AiQuotaDenialReason.CONTEXT_LIMIT_EXCEEDED);
        }

        UtcDayWindow window = utcDayWindow();
        long assistantUsageCount = usageLogRepository
                .countByUserIdAndAssistantKeyAndCreatedAtGreaterThanEqualAndCreatedAtLessThanAndActiveTrueAndDeletedFalse(
                        request.userId(), request.assistantKey(), window.startInclusive(), window.endExclusive());
        if (assistantUsageCount >= assistant.getMaxRequestsPerDay()) {
            return AiQuotaDecision.deny(AiQuotaDenialReason.ASSISTANT_DAILY_LIMIT_REACHED);
        }

        if (quotaConfig.isPresent()) {
            AiQuotaConfigEntity quota = quotaConfig.get();
            long narrowUsageCount = usageLogRepository
                    .countByUserIdAndAssistantKeyAndRoleAndModuleAndCreatedAtGreaterThanEqualAndCreatedAtLessThanAndActiveTrueAndDeletedFalse(
                            request.userId(), request.assistantKey(), role, module, window.startInclusive(),
                            window.endExclusive());
            if (narrowUsageCount >= quota.getMaxRequestsPerDay()) {
                return AiQuotaDecision.deny(AiQuotaDenialReason.QUOTA_DAILY_LIMIT_REACHED);
            }
        }

        return AiQuotaDecision.allow();
    }

    private UtcDayWindow utcDayWindow() {
        LocalDate currentUtcDate = clock.instant().atOffset(ZoneOffset.UTC).toLocalDate();
        Instant startInclusive = currentUtcDate.atStartOfDay().toInstant(ZoneOffset.UTC);
        return new UtcDayWindow(startInclusive, startInclusive.plusSeconds(24 * 60 * 60));
    }

    private String normalizeRole(String role) {
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("ROLE_")) {
            normalized = normalized.substring("ROLE_".length());
        }
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("role is required");
        }
        return normalized;
    }

    private String normalizeModule(String module) {
        return module.trim().toLowerCase(Locale.ROOT);
    }

    private boolean validQuotaConfig(AiQuotaConfigEntity quota, AiAssistantKey key) {
        return quota.getAssistantKey() == key && hasText(quota.getRole()) && hasText(quota.getModule())
                && positive(quota.getMaxRequestsPerDay()) && positive(quota.getMaxContextTokens());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean positive(Integer value) {
        return value != null && value > 0;
    }

    private record UtcDayWindow(Instant startInclusive, Instant endExclusive) {
    }
}
