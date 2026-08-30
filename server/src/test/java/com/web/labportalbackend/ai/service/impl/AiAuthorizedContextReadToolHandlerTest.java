package com.web.labportalbackend.ai.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.web.labportalbackend.ai.context.AiAuthorizedContext;
import com.web.labportalbackend.ai.context.AiLabContext;
import com.web.labportalbackend.ai.enums.AiActionRiskBoundary;
import com.web.labportalbackend.ai.enums.AiAssistantDomain;
import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.ai.enums.AiCapability;
import com.web.labportalbackend.ai.enums.AiResourceScope;
import com.web.labportalbackend.ai.enums.AiToolId;
import com.web.labportalbackend.ai.service.AiAuthorizedToolPolicy;
import com.web.labportalbackend.ai.service.AiCapabilityDecision;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class AiAuthorizedContextReadToolHandlerTest {

    private final AiToolRegistryServiceImpl registry = new AiToolRegistryServiceImpl();
    private final AiAuthorizedContextReadToolHandler handler = new AiAuthorizedContextReadToolHandler(registry);

    @Test
    void registersExactlyTheCanonicalReadOnlySubset() {
        Set<AiToolId> expected = Arrays.stream(AiToolId.values())
                .filter(id -> registry.get(id).riskBoundary() == AiActionRiskBoundary.READ_ONLY)
                .collect(Collectors.toUnmodifiableSet());

        assertEquals(expected, handler.supportedToolIds());
    }

    @Test
    void returnsOnlyTheAlreadyAuthorizedBoundedContextForTheExactTool() {
        AiAuthorizedContext authorized = context(AiCapability.LAB_SLOT_READ);

        assertSame(authorized.context(), handler.execute(AiToolId.LAB_SLOT_READ, authorized));
        assertThrows(IllegalArgumentException.class,
                () -> handler.execute(AiToolId.LAB_POLICY_READ, authorized));
        assertThrows(IllegalArgumentException.class,
                () -> handler.execute(AiToolId.LAB_BOOKING_DRAFT, context(AiCapability.LAB_BOOKING_DRAFT)));
    }

    private AiAuthorizedContext context(AiCapability capability) {
        AiCapabilityDecision.ResolvedResource resource = new AiCapabilityDecision.ResolvedResource(
                capability.resourceType(), 17L, 10L, null, null, null,
                AiResourceScope.EXISTING_BUSINESS_PERMISSION);
        AiLabContext context = new AiLabContext(
                new AiLabContext.Laboratory(10L, "Lab", null), null, null, null, null,
                capability.riskBoundary() == AiActionRiskBoundary.DRAFT_ONLY, null);
        return new AiAuthorizedContext("request", AiAssistantKey.LAB_ASSISTANT, AiAssistantDomain.LAB,
                capability, resource, new AiAuthorizedToolPolicy(registry.get(capability)),
                "P5A-T5-v1", Instant.EPOCH, AiAuthorizedContext.Freshness.LIVE_READ_NO_CACHE, context);
    }
}
