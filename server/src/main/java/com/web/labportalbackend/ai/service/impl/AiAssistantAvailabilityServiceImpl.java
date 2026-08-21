package com.web.labportalbackend.ai.service.impl;

import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.ai.enums.AiAssistantSystemRole;
import com.web.labportalbackend.ai.enums.AiQuotaPolicyReference;
import com.web.labportalbackend.ai.service.AiAssistantAvailabilityException;
import com.web.labportalbackend.ai.service.AiAssistantAvailabilityFailure;
import com.web.labportalbackend.ai.service.AiAssistantAvailability;
import com.web.labportalbackend.ai.service.AiAssistantAvailabilityService;
import com.web.labportalbackend.ai.service.AiAssistantProfile;
import com.web.labportalbackend.ai.service.AiAssistantRegistry;
import com.web.labportalbackend.ai.service.AiAssistantRegistryException;
import com.web.labportalbackend.ai.service.AiConfigQuotaService;
import com.web.labportalbackend.ai.service.AiQuotaCheckRequest;
import com.web.labportalbackend.ai.service.AiQuotaDecision;
import com.web.labportalbackend.ai.service.AiQuotaDenialReason;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.enums.UserStatus;
import java.util.Arrays;
import java.util.Locale;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiAssistantAvailabilityServiceImpl implements AiAssistantAvailabilityService {

    private static final int PRE_CONTEXT_TOKEN_COUNT = 0;

    private final AiAssistantRegistry assistantRegistry;
    private final AiConfigQuotaService configQuotaService;
    private final UserRepository userRepository;

    public AiAssistantAvailabilityServiceImpl(AiAssistantRegistry assistantRegistry,
                                              AiConfigQuotaService configQuotaService,
                                              UserRepository userRepository) {
        this.assistantRegistry = assistantRegistry;
        this.configQuotaService = configQuotaService;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public AiAssistantProfile requireAvailable(AiAssistantKey assistantKey) {
        return evaluateAvailability(assistantKey).profile();
    }

    @Override
    @Transactional(readOnly = true)
    public AiAssistantAvailability requireAvailableForActor(AiAssistantKey assistantKey) {
        return evaluateAvailability(assistantKey);
    }

    private AiAssistantAvailability evaluateAvailability(AiAssistantKey assistantKey) {
        AiAssistantProfile profile = resolveProfile(assistantKey);
        if (!validProfile(profile, assistantKey)) {
            throw denied(AiAssistantAvailabilityFailure.CONFIGURATION_UNAVAILABLE);
        }
        if (!profile.catalogEnabled()) {
            throw denied(AiAssistantAvailabilityFailure.ASSISTANT_UNAVAILABLE);
        }

        User actor = resolveActor();
        AiAssistantSystemRole role = Arrays.stream(AiAssistantSystemRole.values())
                .filter(profile.allowedSystemRoles()::contains)
                .filter(candidate -> actor.hasRole(candidate.name()))
                .findFirst()
                .orElseThrow(() -> denied(AiAssistantAvailabilityFailure.ROLE_NOT_ALLOWED));

        AiAssistantProfile availableProfile = resolveAvailableProfile(assistantKey, role);
        if (!profile.equals(availableProfile)) {
            throw denied(AiAssistantAvailabilityFailure.CONFIGURATION_UNAVAILABLE);
        }
        if (profile.quotaPolicyReference() != AiQuotaPolicyReference.AI_CONFIG_QUOTA) {
            throw denied(AiAssistantAvailabilityFailure.CONFIGURATION_UNAVAILABLE);
        }

        AiQuotaDecision quotaDecision;
        try {
            quotaDecision = configQuotaService.evaluate(new AiQuotaCheckRequest(
                    actor.getId(), assistantKey, role.name(),
                    profile.domain().name().toLowerCase(Locale.ROOT), PRE_CONTEXT_TOKEN_COUNT));
        } catch (RuntimeException exception) {
            throw denied(AiAssistantAvailabilityFailure.CONFIGURATION_UNAVAILABLE, exception);
        }
        if (quotaDecision == null || (!quotaDecision.allowed() && quotaDecision.denialReason() == null)) {
            throw denied(AiAssistantAvailabilityFailure.CONFIGURATION_UNAVAILABLE);
        }
        if (!quotaDecision.allowed()) {
            throw denied(mapQuotaFailure(quotaDecision.denialReason()));
        }
        return new AiAssistantAvailability(profile, actor.getId(), role);
    }

    private AiAssistantProfile resolveProfile(AiAssistantKey assistantKey) {
        try {
            return assistantRegistry.getProfile(assistantKey);
        } catch (AiAssistantRegistryException exception) {
            throw denied(mapRegistryFailure(exception), exception);
        } catch (RuntimeException exception) {
            throw denied(AiAssistantAvailabilityFailure.CONFIGURATION_UNAVAILABLE, exception);
        }
    }

    private AiAssistantProfile resolveAvailableProfile(AiAssistantKey assistantKey, AiAssistantSystemRole role) {
        try {
            return assistantRegistry.getAvailableProfile(assistantKey, role);
        } catch (AiAssistantRegistryException exception) {
            throw denied(mapRegistryFailure(exception), exception);
        } catch (RuntimeException exception) {
            throw denied(AiAssistantAvailabilityFailure.CONFIGURATION_UNAVAILABLE, exception);
        }
    }

    private User resolveActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || authentication.getName() == null || authentication.getName().isBlank()
                || "anonymousUser".equals(authentication.getName())) {
            throw denied(AiAssistantAvailabilityFailure.UNAUTHENTICATED);
        }

        User actor;
        try {
            actor = userRepository.findByUsername(authentication.getName()).orElse(null);
        } catch (RuntimeException exception) {
            throw denied(AiAssistantAvailabilityFailure.ACTOR_UNAVAILABLE, exception);
        }
        if (actor == null || actor.getId() == null || !Boolean.TRUE.equals(actor.getActive())
                || Boolean.TRUE.equals(actor.getDeleted()) || actor.getStatus() != UserStatus.ACTIVE
                || actor.getRoles() == null) {
            throw denied(AiAssistantAvailabilityFailure.ACTOR_UNAVAILABLE);
        }
        return actor;
    }

    private static boolean validProfile(AiAssistantProfile profile, AiAssistantKey assistantKey) {
        return profile != null && assistantKey != null && profile.key() == assistantKey
                && assistantKey.matchesDomain(profile.domain())
                && profile.allowedSystemRoles() != null && !profile.allowedSystemRoles().isEmpty()
                && profile.allowedSystemRoles().stream().noneMatch(role -> role == null)
                && hasText(profile.modelProfile()) && hasText(profile.promptVersion())
                && hasText(profile.retrievalNamespace()) && hasText(profile.evaluationSuiteVersion())
                && profile.quotaPolicyReference() != null
                && profile.toolGroups() != null && !profile.toolGroups().isEmpty()
                && profile.toolGroups().stream().allMatch(group -> group != null && group.belongsTo(profile.domain()));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static AiAssistantAvailabilityFailure mapRegistryFailure(AiAssistantRegistryException exception) {
        if (exception.failure() == null) {
            return AiAssistantAvailabilityFailure.CONFIGURATION_UNAVAILABLE;
        }
        return switch (exception.failure()) {
            case UNKNOWN_ASSISTANT, ASSISTANT_UNAVAILABLE -> AiAssistantAvailabilityFailure.ASSISTANT_UNAVAILABLE;
            case ROLE_INELIGIBLE -> AiAssistantAvailabilityFailure.ROLE_NOT_ALLOWED;
            case MALFORMED_CATALOG -> AiAssistantAvailabilityFailure.CONFIGURATION_UNAVAILABLE;
        };
    }

    private static AiAssistantAvailabilityFailure mapQuotaFailure(AiQuotaDenialReason reason) {
        return switch (reason) {
            case ASSISTANT_DISABLED -> AiAssistantAvailabilityFailure.ASSISTANT_UNAVAILABLE;
            case ASSISTANT_CONFIGURATION_UNAVAILABLE, QUOTA_DISABLED ->
                    AiAssistantAvailabilityFailure.CONFIGURATION_UNAVAILABLE;
            case CONTEXT_LIMIT_EXCEEDED, ASSISTANT_DAILY_LIMIT_REACHED, QUOTA_DAILY_LIMIT_REACHED ->
                    AiAssistantAvailabilityFailure.QUOTA_EXCEEDED;
        };
    }

    private static AiAssistantAvailabilityException denied(AiAssistantAvailabilityFailure failure) {
        return new AiAssistantAvailabilityException(failure);
    }

    private static AiAssistantAvailabilityException denied(AiAssistantAvailabilityFailure failure,
                                                            Throwable cause) {
        return new AiAssistantAvailabilityException(failure, cause);
    }
}
