package com.web.labportalbackend.ai.context;

import com.web.labportalbackend.ai.enums.AiAssistantDomain;
import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.ai.enums.AiCapability;
import com.web.labportalbackend.ai.service.AiAuthorizedToolPolicy;
import com.web.labportalbackend.ai.service.AiCapabilityDecision;
import java.time.Instant;

public record AiAuthorizedContext(
        String requestId,
        AiAssistantKey assistantKey,
        AiAssistantDomain domain,
        AiCapability capability,
        AiCapabilityDecision.ResolvedResource resource,
        AiAuthorizedToolPolicy toolPolicy,
        String contextVersion,
        Instant builtAt,
        Freshness freshness,
        AiDomainContext context) {

    public enum Freshness { LIVE_READ_NO_CACHE }

    public AiAuthorizedContext {
        if (assistantKey == null || domain == null || capability == null || resource == null || toolPolicy == null
                || contextVersion == null || contextVersion.isBlank() || builtAt == null
                || freshness == null || context == null) {
            throw new IllegalArgumentException("authorized context is incomplete");
        }
        if (!assistantKey.matchesDomain(domain) || capability.domain() != domain
                || toolPolicy.descriptor().domain() != domain || toolPolicy.descriptor().capability() != capability
                || toolPolicy.descriptor().resourceType() != resource.type()
                || toolPolicy.descriptor().riskBoundary() != capability.riskBoundary()
                || !toolPolicy.descriptor().matchesResolvedResource(resource)) {
            throw new IllegalArgumentException("authorized context policy does not match authority");
        }
    }
}
