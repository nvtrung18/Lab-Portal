package com.web.labportalbackend.ai.service.impl;

import com.web.labportalbackend.ai.entity.AiAssistantConfigEntity;
import com.web.labportalbackend.ai.enums.AiAssistantDomain;
import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.ai.enums.AiAssistantSystemRole;
import com.web.labportalbackend.ai.enums.AiAssistantToolGroup;
import com.web.labportalbackend.ai.enums.AiQuotaPolicyReference;
import com.web.labportalbackend.ai.repository.AiAssistantConfigRepository;
import com.web.labportalbackend.ai.service.AiAssistantProfile;
import com.web.labportalbackend.ai.service.AiAssistantRegistry;
import com.web.labportalbackend.ai.service.AiAssistantRegistryException;
import com.web.labportalbackend.ai.service.AiAssistantRegistryFailure;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiAssistantRegistryServiceImpl implements AiAssistantRegistry {

    private final AiAssistantConfigRepository assistantConfigRepository;
    private final Map<AiAssistantKey, AiAssistantProfile> profiles;

    public AiAssistantRegistryServiceImpl(AiAssistantConfigRepository assistantConfigRepository) {
        this(assistantConfigRepository, defaultCatalog());
    }

    AiAssistantRegistryServiceImpl(AiAssistantConfigRepository assistantConfigRepository,
                                   List<AiAssistantProfile> catalog) {
        if (assistantConfigRepository == null) {
            throw new IllegalArgumentException("assistantConfigRepository is required");
        }
        this.assistantConfigRepository = assistantConfigRepository;
        this.profiles = validatedCatalog(catalog);
    }

    @Override
    public AiAssistantProfile getProfile(AiAssistantKey assistantKey) {
        if (assistantKey == null) {
            throw new AiAssistantRegistryException(AiAssistantRegistryFailure.UNKNOWN_ASSISTANT);
        }
        AiAssistantProfile profile = profiles.get(assistantKey);
        if (profile == null) {
            throw new AiAssistantRegistryException(AiAssistantRegistryFailure.UNKNOWN_ASSISTANT);
        }
        return profile;
    }

    @Override
    @Transactional(readOnly = true)
    public AiAssistantProfile getAvailableProfile(AiAssistantKey assistantKey, AiAssistantSystemRole systemRole) {
        AiAssistantProfile profile = getProfile(assistantKey);
        if (systemRole == null || !profile.allowedSystemRoles().contains(systemRole)) {
            throw new AiAssistantRegistryException(AiAssistantRegistryFailure.ROLE_INELIGIBLE);
        }
        if (!profile.catalogEnabled()) {
            throw new AiAssistantRegistryException(AiAssistantRegistryFailure.ASSISTANT_UNAVAILABLE);
        }

        return assistantConfigRepository.findByAssistantKeyAndActiveTrueAndDeletedFalse(assistantKey)
                .filter(config -> Boolean.TRUE.equals(config.getEnabled()))
                .map(config -> profile)
                .orElseThrow(() -> new AiAssistantRegistryException(AiAssistantRegistryFailure.ASSISTANT_UNAVAILABLE));
    }

    static List<AiAssistantProfile> defaultCatalog() {
        return List.of(
                new AiAssistantProfile(AiAssistantKey.ADMIN_ASSISTANT, AiAssistantDomain.ADMIN, false,
                        Set.of(AiAssistantSystemRole.ADMIN), "admin", "admin-v1", null, "admin",
                        AiQuotaPolicyReference.AI_CONFIG_QUOTA,
                        Set.of(AiAssistantToolGroup.ADMIN_READ, AiAssistantToolGroup.ADMIN_DRAFT),
                        "admin-assistant-v1"),
                new AiAssistantProfile(AiAssistantKey.LAB_ASSISTANT, AiAssistantDomain.LAB, true,
                        Set.of(AiAssistantSystemRole.ADMIN, AiAssistantSystemRole.LAB_MANAGER, AiAssistantSystemRole.STUDENT),
                        "lab", "lab-v1", null, "lab", AiQuotaPolicyReference.AI_CONFIG_QUOTA,
                        Set.of(AiAssistantToolGroup.LAB_READ, AiAssistantToolGroup.LAB_DRAFT), "lab-assistant-v1"),
                new AiAssistantProfile(AiAssistantKey.RESEARCH_ASSISTANT, AiAssistantDomain.RESEARCH, true,
                        Set.of(AiAssistantSystemRole.ADMIN, AiAssistantSystemRole.LAB_MANAGER, AiAssistantSystemRole.STUDENT),
                        "research", "research-v1", null, "research", AiQuotaPolicyReference.AI_CONFIG_QUOTA,
                        Set.of(AiAssistantToolGroup.RESEARCH_READ, AiAssistantToolGroup.RESEARCH_DRAFT),
                        "research-assistant-v1"));
    }

    private static Map<AiAssistantKey, AiAssistantProfile> validatedCatalog(List<AiAssistantProfile> catalog) {
        if (catalog == null || catalog.size() != AiAssistantKey.values().length) {
            throw malformedCatalog();
        }

        Map<AiAssistantKey, AiAssistantProfile> profilesByKey = new EnumMap<>(AiAssistantKey.class);
        for (AiAssistantProfile profile : catalog) {
            if (profile == null || profilesByKey.put(profile.key(), profile) != null) {
                throw malformedCatalog();
            }
        }

        Map<AiAssistantKey, AiAssistantProfile> expectedProfiles = new EnumMap<>(AiAssistantKey.class);
        for (AiAssistantProfile profile : defaultCatalog()) {
            expectedProfiles.put(profile.key(), profile);
        }
        if (!profilesByKey.equals(expectedProfiles)) {
            throw malformedCatalog();
        }
        return Map.copyOf(profilesByKey);
    }

    private static AiAssistantRegistryException malformedCatalog() {
        return new AiAssistantRegistryException(AiAssistantRegistryFailure.MALFORMED_CATALOG);
    }
}
