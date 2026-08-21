package com.web.labportalbackend.ai.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.web.labportalbackend.ai.enums.AiActionRiskBoundary;
import com.web.labportalbackend.ai.enums.AiToolId;
import com.web.labportalbackend.ai.service.AiToolActionGateDecision;
import com.web.labportalbackend.ai.service.AiToolDefinition;
import com.web.labportalbackend.ai.service.AiToolRegistry;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AiToolActionGateServiceImplTest {

    private final AiToolRegistry registry = mock(AiToolRegistry.class);
    private final AiToolActionGateServiceImpl service = new AiToolActionGateServiceImpl(registry);

    @ParameterizedTest
    @MethodSource("riskDecisions")
    void classifiesOnlyTheCanonicalSpringToolDefinition(AiActionRiskBoundary riskBoundary,
                                                         AiToolActionGateDecision expected) {
        AiToolDefinition canonical = canonicalDefinition(riskBoundary);

        assertEquals(expected, service.classify(canonical));
        verify(registry).get(AiToolId.LAB_BOOKING_DRAFT);
    }

    @Test
    void unknownMissingOrSubstitutedPolicyFailsClosed() {
        AiToolDefinition missingId = mock(AiToolDefinition.class);
        assertEquals(AiToolActionGateDecision.DENY, service.classify(null));
        assertEquals(AiToolActionGateDecision.DENY, service.classify(missingId));
        verify(registry, never()).get(AiToolId.LAB_BOOKING_DRAFT);

        AiToolDefinition unknown = mock(AiToolDefinition.class);
        when(unknown.id()).thenReturn(AiToolId.LAB_BOOKING_DRAFT);
        assertEquals(AiToolActionGateDecision.DENY, service.classify(unknown));

        AiToolDefinition substituted = mock(AiToolDefinition.class);
        when(substituted.id()).thenReturn(AiToolId.LAB_BOOKING_DRAFT);
        canonicalDefinition(AiActionRiskBoundary.READ_ONLY);
        assertEquals(AiToolActionGateDecision.DENY, service.classify(substituted));
    }

    @Test
    void malformedCanonicalRiskFailsClosed() {
        AiToolDefinition canonical = canonicalDefinition(null);

        assertEquals(AiToolActionGateDecision.DENY, service.classify(canonical));
    }

    @Test
    void registryFailureFailsClosed() {
        AiToolDefinition candidate = mock(AiToolDefinition.class);
        when(candidate.id()).thenReturn(AiToolId.LAB_BOOKING_DRAFT);
        when(registry.get(AiToolId.LAB_BOOKING_DRAFT)).thenThrow(new IllegalStateException("internal"));

        assertEquals(AiToolActionGateDecision.DENY, service.classify(candidate));
    }

    @Test
    void realCatalogPreservesReadOnlyAndDraftOnlySemantics() {
        AiToolRegistry realRegistry = new AiToolRegistryServiceImpl();
        AiToolActionGateServiceImpl realService = new AiToolActionGateServiceImpl(realRegistry);

        assertEquals(AiToolActionGateDecision.ALLOW_READ_ONLY,
                realService.classify(realRegistry.get(AiToolId.LAB_SLOT_READ)));
        assertEquals(AiToolActionGateDecision.RETURN_DRAFT_ONLY,
                realService.classify(realRegistry.get(AiToolId.LAB_BOOKING_DRAFT)));
    }

    private AiToolDefinition canonicalDefinition(AiActionRiskBoundary riskBoundary) {
        AiToolDefinition definition = mock(AiToolDefinition.class);
        when(definition.id()).thenReturn(AiToolId.LAB_BOOKING_DRAFT);
        when(definition.riskBoundary()).thenReturn(riskBoundary);
        when(registry.get(AiToolId.LAB_BOOKING_DRAFT)).thenReturn(definition);
        return definition;
    }

    private static Stream<Arguments> riskDecisions() {
        return Stream.of(
                Arguments.of(AiActionRiskBoundary.READ_ONLY, AiToolActionGateDecision.ALLOW_READ_ONLY),
                Arguments.of(AiActionRiskBoundary.DRAFT_ONLY, AiToolActionGateDecision.RETURN_DRAFT_ONLY),
                Arguments.of(AiActionRiskBoundary.CONFIRM_REQUIRED, AiToolActionGateDecision.REQUIRE_CONFIRMATION),
                Arguments.of(AiActionRiskBoundary.APPROVAL_REQUIRED, AiToolActionGateDecision.REQUIRE_APPROVAL),
                Arguments.of(AiActionRiskBoundary.PROHIBITED, AiToolActionGateDecision.DENY));
    }
}
