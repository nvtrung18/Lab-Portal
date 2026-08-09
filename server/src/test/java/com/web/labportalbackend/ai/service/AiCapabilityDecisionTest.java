package com.web.labportalbackend.ai.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.web.labportalbackend.ai.enums.AiActionRiskBoundary;
import com.web.labportalbackend.ai.enums.AiAssistantDomain;
import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.ai.enums.AiAssistantSystemRole;
import com.web.labportalbackend.ai.enums.AiCapability;
import com.web.labportalbackend.ai.enums.AiCapabilityDecisionReason;
import com.web.labportalbackend.ai.enums.AiCapabilityDenialReason;
import com.web.labportalbackend.ai.enums.AiResourceScope;
import com.web.labportalbackend.ai.enums.AiResourceType;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AiCapabilityDecisionTest {

    @Test
    void acceptedActorBindingIsRequiredForAllowedAndForbiddenForDeniedDecisions() {
        var resource = new AiCapabilityDecision.ResolvedResource(AiResourceType.LABORATORY, 10L, 10L,
                null, null, null, AiResourceScope.EXISTING_BUSINESS_PERMISSION);
        assertThrows(IllegalArgumentException.class, () -> decision(true, null, resource, null));
        assertThrows(IllegalArgumentException.class, () -> decision(true, 0L, resource, null));
        assertThrows(IllegalArgumentException.class, () -> decision(true, 7L, null, null));
        assertThrows(IllegalArgumentException.class, () -> decision(false, 7L, null,
                AiCapabilityDenialReason.ACTOR_MISMATCH));
    }

    @Test
    void selectedRoleIsRequiredForAllowedDecisionsAndForbiddenForDeniedDecisions() {
        var resource = new AiCapabilityDecision.ResolvedResource(AiResourceType.LABORATORY, 10L, 10L,
                null, null, null, AiResourceScope.EXISTING_BUSINESS_PERMISSION);

        assertThrows(IllegalArgumentException.class, () -> new AiCapabilityDecision(true, 7L, null,
                AiAssistantKey.LAB_ASSISTANT, AiAssistantDomain.LAB, AiCapability.LAB_POLICY_READ, resource,
                AiCapabilityDecisionReason.ALLOWED_BY_EFFECTIVE_PERMISSION, null,
                AiActionRiskBoundary.READ_ONLY, Set.of(), null));
        assertThrows(IllegalArgumentException.class, () -> new AiCapabilityDecision(false, null,
                AiAssistantSystemRole.STUDENT, AiAssistantKey.LAB_ASSISTANT, AiAssistantDomain.LAB,
                AiCapability.LAB_POLICY_READ, null, AiCapabilityDecisionReason.DENIED_BY_REQUEST,
                AiCapabilityDenialReason.ACTOR_MISMATCH, AiActionRiskBoundary.READ_ONLY, Set.of(), null));
        assertDoesNotThrow(() -> new AiCapabilityDecision(true, 7L, AiAssistantSystemRole.STUDENT,
                AiAssistantKey.LAB_ASSISTANT, AiAssistantDomain.LAB, AiCapability.LAB_POLICY_READ, resource,
                AiCapabilityDecisionReason.ALLOWED_BY_EFFECTIVE_PERMISSION, null,
                AiActionRiskBoundary.READ_ONLY, Set.of(), null));
    }

    @Test
    void checkinGuidanceRequiresAnInclusiveResolverSnapshotAndOtherCapabilitiesRejectIt() {
        var booking = new AiCapabilityDecision.ResolvedResource(AiResourceType.BOOKING, 10L, 10L,
                null, null, null, AiResourceScope.SELF);
        var snapshot = new AiCapabilityDecision.CheckinGuidancePolicySnapshot(Instant.parse("2026-08-07T09:30:00Z"));
        assertThrows(IllegalArgumentException.class, () -> new AiCapabilityDecision(true, 7L, AiAssistantSystemRole.STUDENT,
                AiAssistantKey.LAB_ASSISTANT, AiAssistantDomain.LAB, AiCapability.LAB_CHECKIN_GUIDANCE, booking,
                AiCapabilityDecisionReason.ALLOWED_BY_EFFECTIVE_PERMISSION, null, AiActionRiskBoundary.READ_ONLY,
                Set.of(), null));
        assertThrows(IllegalArgumentException.class, () -> new AiCapabilityDecision(true, 7L, AiAssistantSystemRole.STUDENT,
                AiAssistantKey.LAB_ASSISTANT, AiAssistantDomain.LAB, AiCapability.LAB_POLICY_READ,
                new AiCapabilityDecision.ResolvedResource(AiResourceType.LABORATORY, 10L, 10L,
                        null, null, null, AiResourceScope.EXISTING_BUSINESS_PERMISSION),
                AiCapabilityDecisionReason.ALLOWED_BY_EFFECTIVE_PERMISSION, null, AiActionRiskBoundary.READ_ONLY,
                Set.of(), snapshot));
    }

    @Test
    void deniedCheckinGuidanceKeepsTheSnapshotNullAndRejectsSnapshotEvidence() {
        assertDoesNotThrow(() -> new AiCapabilityDecision(false, null, null,
                AiAssistantKey.LAB_ASSISTANT, AiAssistantDomain.LAB, AiCapability.LAB_CHECKIN_GUIDANCE, null,
                AiCapabilityDecisionReason.DENIED_BY_REQUEST, AiCapabilityDenialReason.ACTOR_MISMATCH,
                AiActionRiskBoundary.READ_ONLY, Set.of(), null));
        assertThrows(IllegalArgumentException.class, () -> new AiCapabilityDecision(false, null, null,
                AiAssistantKey.LAB_ASSISTANT, AiAssistantDomain.LAB, AiCapability.LAB_CHECKIN_GUIDANCE, null,
                AiCapabilityDecisionReason.DENIED_BY_REQUEST, AiCapabilityDenialReason.ACTOR_MISMATCH,
                AiActionRiskBoundary.READ_ONLY, Set.of(),
                new AiCapabilityDecision.CheckinGuidancePolicySnapshot(Instant.parse("2026-08-07T09:30:00Z"))));
    }

    private AiCapabilityDecision decision(boolean allowed, Long actorId, AiCapabilityDecision.ResolvedResource resource,
                                          AiCapabilityDenialReason denialReason) {
        return new AiCapabilityDecision(allowed, actorId, allowed ? AiAssistantSystemRole.STUDENT : null,
                AiAssistantKey.LAB_ASSISTANT, AiAssistantDomain.LAB,
                AiCapability.LAB_POLICY_READ, resource,
                allowed ? AiCapabilityDecisionReason.ALLOWED_BY_EFFECTIVE_PERMISSION
                        : AiCapabilityDecisionReason.DENIED_BY_REQUEST,
                denialReason, AiActionRiskBoundary.READ_ONLY, Set.of(), null);
    }
}
