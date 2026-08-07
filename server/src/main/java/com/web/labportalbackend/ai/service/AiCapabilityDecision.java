package com.web.labportalbackend.ai.service;

import com.web.labportalbackend.ai.enums.AiActionRiskBoundary;
import com.web.labportalbackend.ai.enums.AiAssistantDomain;
import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.ai.enums.AiCapability;
import com.web.labportalbackend.ai.enums.AiCapabilityDecisionReason;
import com.web.labportalbackend.ai.enums.AiCapabilityDenialReason;
import com.web.labportalbackend.ai.enums.AiCapabilityEvidence;
import com.web.labportalbackend.ai.enums.AiResourceScope;
import com.web.labportalbackend.ai.enums.AiResourceType;
import java.util.Set;

public record AiCapabilityDecision(
        boolean allowed,
        AiAssistantKey assistantKey,
        AiAssistantDomain domain,
        AiCapability capability,
        ResolvedResource resolvedResource,
        AiCapabilityDecisionReason decisionReason,
        AiCapabilityDenialReason denialReason,
        AiActionRiskBoundary riskBoundary,
        Set<AiCapabilityEvidence> evidence) {

    public AiCapabilityDecision {
        if (decisionReason == null) {
            throw new IllegalArgumentException("decisionReason is required");
        }
        if (allowed == (denialReason != null)) {
            throw new IllegalArgumentException("allowed and denialReason are inconsistent");
        }
        evidence = evidence == null ? Set.of() : Set.copyOf(evidence);
    }

    public record ResolvedResource(
            AiResourceType type,
            Long id,
            Long labId,
            Long projectId,
            Long groupId,
            Long taskId,
            AiResourceScope effectiveScope) {

        public ResolvedResource {
            if (type == null || effectiveScope == null) {
                throw new IllegalArgumentException("resolved resource type and scope are required");
            }
        }
    }
}
