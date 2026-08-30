package com.web.labportalbackend.ai.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiAssistantRegistryServiceImplTest {

    @Mock AiAssistantConfigRepository assistantConfigRepository;

    private AiAssistantRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new AiAssistantRegistryServiceImpl(assistantConfigRepository);
    }

    @Test
    void exposesExactlyTheContractProfilesWithImmutableMetadata() {
        AiAssistantProfile admin = registry.getProfile(AiAssistantKey.ADMIN_ASSISTANT);
        AiAssistantProfile lab = registry.getProfile(AiAssistantKey.LAB_ASSISTANT);
        AiAssistantProfile research = registry.getProfile(AiAssistantKey.RESEARCH_ASSISTANT);

        assertProfile(admin, AiAssistantDomain.ADMIN, true, Set.of(AiAssistantSystemRole.ADMIN), "admin", "admin-v1",
                "admin", Set.of(AiAssistantToolGroup.ADMIN_READ, AiAssistantToolGroup.ADMIN_DRAFT),
                "admin-assistant-v1");
        assertProfile(lab, AiAssistantDomain.LAB, true,
                Set.of(AiAssistantSystemRole.ADMIN, AiAssistantSystemRole.LAB_MANAGER, AiAssistantSystemRole.STUDENT),
                "lab", "lab-v1", "lab", Set.of(AiAssistantToolGroup.LAB_READ, AiAssistantToolGroup.LAB_DRAFT),
                "lab-assistant-v1");
        assertProfile(research, AiAssistantDomain.RESEARCH, true,
                Set.of(AiAssistantSystemRole.ADMIN, AiAssistantSystemRole.LAB_MANAGER, AiAssistantSystemRole.STUDENT),
                "research", "research-v1", "research",
                Set.of(AiAssistantToolGroup.RESEARCH_READ, AiAssistantToolGroup.RESEARCH_DRAFT),
                "research-assistant-v1");

        assertThrows(UnsupportedOperationException.class,
                () -> lab.allowedSystemRoles().add(AiAssistantSystemRole.ADMIN));
        assertThrows(UnsupportedOperationException.class,
                () -> lab.toolGroups().add(AiAssistantToolGroup.ADMIN_READ));
    }

    @Test
    void rejectsUnknownAssistantKeysWithoutLookingUpConfiguration() {
        AiAssistantRegistryException exception = assertThrows(AiAssistantRegistryException.class,
                () -> registry.getProfile(null));

        assertEquals(AiAssistantRegistryFailure.UNKNOWN_ASSISTANT, exception.failure());
        verifyNoInteractions(assistantConfigRepository);
    }

    @Test
    void rejectsRoleIneligibleAdminBeforeConfigurationLookup() {
        AiAssistantRegistryException exception = assertThrows(AiAssistantRegistryException.class,
                () -> registry.getAvailableProfile(AiAssistantKey.ADMIN_ASSISTANT, AiAssistantSystemRole.STUDENT));

        assertEquals(AiAssistantRegistryFailure.ROLE_INELIGIBLE, exception.failure());
        verifyNoInteractions(assistantConfigRepository);
    }

    @Test
    void returnsAvailableAdminProfileOnlyForAnEnabledActiveConfiguration() {
        when(assistantConfigRepository.findByAssistantKeyAndActiveTrueAndDeletedFalse(AiAssistantKey.ADMIN_ASSISTANT))
                .thenReturn(Optional.of(config(AiAssistantKey.ADMIN_ASSISTANT, true)));

        AiAssistantProfile profile = registry.getAvailableProfile(AiAssistantKey.ADMIN_ASSISTANT,
                AiAssistantSystemRole.ADMIN);

        assertEquals(AiAssistantKey.ADMIN_ASSISTANT, profile.key());
        verify(assistantConfigRepository)
                .findByAssistantKeyAndActiveTrueAndDeletedFalse(AiAssistantKey.ADMIN_ASSISTANT);
    }

    @Test
    void returnsAvailableLabProfileOnlyForAnEnabledActiveConfiguration() {
        when(assistantConfigRepository.findByAssistantKeyAndActiveTrueAndDeletedFalse(AiAssistantKey.LAB_ASSISTANT))
                .thenReturn(Optional.of(config(AiAssistantKey.LAB_ASSISTANT, true)));

        AiAssistantProfile profile = registry.getAvailableProfile(AiAssistantKey.LAB_ASSISTANT,
                AiAssistantSystemRole.STUDENT);

        assertEquals(AiAssistantKey.LAB_ASSISTANT, profile.key());
        verify(assistantConfigRepository).findByAssistantKeyAndActiveTrueAndDeletedFalse(AiAssistantKey.LAB_ASSISTANT);
    }

    @Test
    void returnsAvailableResearchProfileOnlyForAnEnabledActiveConfiguration() {
        when(assistantConfigRepository.findByAssistantKeyAndActiveTrueAndDeletedFalse(AiAssistantKey.RESEARCH_ASSISTANT))
                .thenReturn(Optional.of(config(AiAssistantKey.RESEARCH_ASSISTANT, true)));

        AiAssistantProfile profile = registry.getAvailableProfile(AiAssistantKey.RESEARCH_ASSISTANT,
                AiAssistantSystemRole.LAB_MANAGER);

        assertEquals(AiAssistantKey.RESEARCH_ASSISTANT, profile.key());
        verify(assistantConfigRepository).findByAssistantKeyAndActiveTrueAndDeletedFalse(AiAssistantKey.RESEARCH_ASSISTANT);
    }

    @Test
    void failsClosedForMissingNullOrFalseEnabledConfiguration() {
        when(assistantConfigRepository.findByAssistantKeyAndActiveTrueAndDeletedFalse(AiAssistantKey.LAB_ASSISTANT))
                .thenReturn(Optional.empty());
        assertUnavailable(AiAssistantKey.LAB_ASSISTANT);

        when(assistantConfigRepository.findByAssistantKeyAndActiveTrueAndDeletedFalse(AiAssistantKey.LAB_ASSISTANT))
                .thenReturn(Optional.of(config(AiAssistantKey.LAB_ASSISTANT, null)));
        assertUnavailable(AiAssistantKey.LAB_ASSISTANT);

        when(assistantConfigRepository.findByAssistantKeyAndActiveTrueAndDeletedFalse(AiAssistantKey.LAB_ASSISTANT))
                .thenReturn(Optional.of(config(AiAssistantKey.LAB_ASSISTANT, false)));
        assertUnavailable(AiAssistantKey.LAB_ASSISTANT);
    }

    @Test
    void rejectsMalformedAndDuplicateCatalogsAtConstruction() {
        List<AiAssistantProfile> defaultCatalog = AiAssistantRegistryServiceImpl.defaultCatalog();
        AiAssistantProfile duplicateLab = defaultCatalog.get(1);
        List<AiAssistantProfile> duplicateCatalog = List.of(defaultCatalog.get(0), duplicateLab, duplicateLab);
        assertMalformed(duplicateCatalog);
        assertThrows(IllegalArgumentException.class, () -> new AiAssistantProfile(
                AiAssistantKey.RESEARCH_ASSISTANT, AiAssistantDomain.LAB, true,
                Set.of(AiAssistantSystemRole.ADMIN), "research", "research-v1", null, "research",
                AiQuotaPolicyReference.AI_CONFIG_QUOTA,
                Set.of(AiAssistantToolGroup.RESEARCH_READ, AiAssistantToolGroup.RESEARCH_DRAFT),
                "research-assistant-v1"));
    }

    private void assertUnavailable(AiAssistantKey key) {
        AiAssistantRegistryException exception = assertThrows(AiAssistantRegistryException.class,
                () -> registry.getAvailableProfile(key, AiAssistantSystemRole.STUDENT));
        assertEquals(AiAssistantRegistryFailure.ASSISTANT_UNAVAILABLE, exception.failure());
    }

    private void assertMalformed(List<AiAssistantProfile> catalog) {
        AiAssistantRegistryException exception = assertThrows(AiAssistantRegistryException.class,
                () -> new AiAssistantRegistryServiceImpl(assistantConfigRepository, catalog));
        assertEquals(AiAssistantRegistryFailure.MALFORMED_CATALOG, exception.failure());
    }

    private static AiAssistantConfigEntity config(AiAssistantKey assistantKey, Boolean enabled) {
        return AiAssistantConfigEntity.builder().assistantKey(assistantKey).enabled(enabled)
                .systemPromptKey("test").maxRequestsPerDay(1).maxContextTokens(1).build();
    }

    private static void assertProfile(AiAssistantProfile profile, AiAssistantDomain domain, boolean catalogEnabled,
                                      Set<AiAssistantSystemRole> roles, String modelProfile, String promptVersion,
                                      String retrievalNamespace, Set<AiAssistantToolGroup> toolGroups,
                                      String evaluationSuiteVersion) {
        assertEquals(domain, profile.domain());
        assertEquals(catalogEnabled, profile.catalogEnabled());
        assertEquals(roles, profile.allowedSystemRoles());
        assertEquals(modelProfile, profile.modelProfile());
        assertEquals(promptVersion, profile.promptVersion());
        assertNull(profile.adapterReference());
        assertEquals(retrievalNamespace, profile.retrievalNamespace());
        assertEquals(AiQuotaPolicyReference.AI_CONFIG_QUOTA, profile.quotaPolicyReference());
        assertEquals(toolGroups, profile.toolGroups());
        assertEquals(evaluationSuiteVersion, profile.evaluationSuiteVersion());
        assertFalse(profile.allowedSystemRoles().isEmpty());
        assertTrue(profile.toolGroups().size() == 2);
    }
}
