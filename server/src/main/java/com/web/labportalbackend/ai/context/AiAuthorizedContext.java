package com.web.labportalbackend.ai.context;

import com.web.labportalbackend.ai.enums.AiAssistantDomain;
import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.ai.enums.AiCapability;
import com.web.labportalbackend.ai.service.AiCapabilityDecision;
import java.time.Instant;

public record AiAuthorizedContext(
        String requestId,
        AiAssistantKey assistantKey,
        AiAssistantDomain domain,
        AiCapability capability,
        AiCapabilityDecision.ResolvedResource resource,
        String contextVersion,
        Instant builtAt,
        Freshness freshness,
        AiDomainContext context) {

    public enum Freshness { LIVE_READ_NO_CACHE }

    public AiAuthorizedContext {
        if (assistantKey == null || domain == null || capability == null || resource == null
                || contextVersion == null || contextVersion.isBlank() || builtAt == null
                || freshness == null || context == null) {
            throw new IllegalArgumentException("authorized context is incomplete");
        }
    }
}
