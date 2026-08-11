package com.web.labportalbackend.ai.context;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.web.labportalbackend.ai.enums.AiAssistantDomain;
import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.ai.enums.AiCapability;
import com.web.labportalbackend.ai.enums.AiResourceScope;
import com.web.labportalbackend.ai.enums.AiResourceType;
import com.web.labportalbackend.ai.service.AiAuthorizedToolPolicy;
import com.web.labportalbackend.ai.service.AiCapabilityDecision;
import com.web.labportalbackend.ai.service.impl.AiToolRegistryServiceImpl;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AiAuthorizedContextTest {

    @Test
    void acceptsOnlyPolicyMetadataMatchingTheCanonicalContextAuthority() {
        assertDoesNotThrow(() -> context(AiCapability.LAB_POLICY_READ));
        assertThrows(IllegalArgumentException.class, () -> new AiAuthorizedContext("request-1",
                AiAssistantKey.LAB_ASSISTANT, AiAssistantDomain.LAB, AiCapability.LAB_POLICY_READ,
                resource(), null, "P5A-T6-v1", Instant.EPOCH, AiAuthorizedContext.Freshness.LIVE_READ_NO_CACHE,
                domainContext()));
        assertThrows(IllegalArgumentException.class, () -> new AiAuthorizedContext("request-1",
                AiAssistantKey.LAB_ASSISTANT, AiAssistantDomain.LAB, AiCapability.LAB_POLICY_READ,
                resource(), new AiAuthorizedToolPolicy(new AiToolRegistryServiceImpl().get(AiCapability.LAB_SLOT_READ)),
                "P5A-T6-v1", Instant.EPOCH, AiAuthorizedContext.Freshness.LIVE_READ_NO_CACHE, domainContext()));
    }

    @Test
    void rejectsGlobalDescriptorWhenAnyBusinessIdentityIsPresent() {
        assertThrows(IllegalArgumentException.class, () -> new AiAuthorizedContext("request-1",
                AiAssistantKey.ADMIN_ASSISTANT, AiAssistantDomain.ADMIN, AiCapability.ADMIN_SYSTEM_SUMMARY,
                new AiCapabilityDecision.ResolvedResource(AiResourceType.SYSTEM, null, 10L,
                        null, null, null, AiResourceScope.GLOBAL),
                new AiAuthorizedToolPolicy(new AiToolRegistryServiceImpl().get(AiCapability.ADMIN_SYSTEM_SUMMARY)),
                "P5A-T6-v1", Instant.EPOCH, AiAuthorizedContext.Freshness.LIVE_READ_NO_CACHE, domainContext()));
    }

    @Test
    void rejectsProjectDescriptorWhenIdDoesNotMatchResolvedProjectIdentity() {
        assertThrows(IllegalArgumentException.class, () -> new AiAuthorizedContext("request-1",
                AiAssistantKey.RESEARCH_ASSISTANT, AiAssistantDomain.RESEARCH, AiCapability.RESEARCH_PROJECT_SUMMARY,
                new AiCapabilityDecision.ResolvedResource(AiResourceType.PROJECT, 10L, 1L,
                        20L, null, null, AiResourceScope.EXISTING_BUSINESS_PERMISSION),
                new AiAuthorizedToolPolicy(new AiToolRegistryServiceImpl().get(AiCapability.RESEARCH_PROJECT_SUMMARY)),
                "P5A-T6-v1", Instant.EPOCH, AiAuthorizedContext.Freshness.LIVE_READ_NO_CACHE, domainContext()));
    }

    private static AiAuthorizedContext context(AiCapability capability) {
        return new AiAuthorizedContext("request-1", AiAssistantKey.LAB_ASSISTANT, AiAssistantDomain.LAB, capability,
                resource(), new AiAuthorizedToolPolicy(new AiToolRegistryServiceImpl().get(capability)), "P5A-T6-v1",
                Instant.EPOCH, AiAuthorizedContext.Freshness.LIVE_READ_NO_CACHE, domainContext());
    }

    private static AiCapabilityDecision.ResolvedResource resource() {
        return new AiCapabilityDecision.ResolvedResource(AiResourceType.LABORATORY, 10L, 10L,
                null, null, null, AiResourceScope.EXISTING_BUSINESS_PERMISSION);
    }

    private static AiDomainContext domainContext() {
        return new AiLabContext(new AiLabContext.Laboratory(10L, "Lab", null), null, null, null,
                false, "POLICY_INFORMATION_ONLY");
    }
}
