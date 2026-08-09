package com.web.labportalbackend.ai.context.impl;

import com.web.labportalbackend.ai.context.AiAuthorizedContext;
import com.web.labportalbackend.ai.context.AiContextReadDeniedException;
import com.web.labportalbackend.ai.context.AiDomainContextBuilder;
import com.web.labportalbackend.ai.context.TrustedContextInput;
import com.web.labportalbackend.ai.enums.AiAssistantDomain;
import com.web.labportalbackend.ai.enums.AiCapabilityDecisionReason;
import com.web.labportalbackend.ai.service.AiCapabilityDecision;
import com.web.labportalbackend.ai.service.AiCapabilityDeniedException;
import com.web.labportalbackend.ai.service.AiCapabilityRequest;
import com.web.labportalbackend.ai.service.AiCapabilityResolver;
import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * A distinct Spring bean so the fresh authorization and every builder projection share one
 * proxied transaction and persistence context, independent of any caller transaction.
 */
@Service
public class AiContextFreshReadExecutor {

    private static final String CONTEXT_VERSION = "P5A-T5-v1";

    private final AiCapabilityResolver capabilityResolver;
    private final Map<AiAssistantDomain, AiDomainContextBuilder> builders;
    private final Clock clock;

    @Autowired
    public AiContextFreshReadExecutor(AiCapabilityResolver capabilityResolver,
                                      List<AiDomainContextBuilder> builders) {
        this(capabilityResolver, builders, Clock.systemUTC());
    }

    AiContextFreshReadExecutor(AiCapabilityResolver capabilityResolver,
                               List<AiDomainContextBuilder> builders,
                               Clock clock) {
        if (capabilityResolver == null || clock == null) {
            throw new IllegalArgumentException("resolver and clock are required");
        }
        this.capabilityResolver = capabilityResolver;
        this.builders = validatedBuilders(builders);
        this.clock = clock;
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public AiAuthorizedContext execute(AiCapabilityDecision preliminary,
                                       AiCapabilityRequest request,
                                       String requestId) {
        if (!isValidDecision(preliminary, request)) {
            throw new AiContextReadDeniedException();
        }
        AiCapabilityDecision current;
        try {
            current = capabilityResolver.requireAllowed(request);
        } catch (AiCapabilityDeniedException exception) {
            throw new AiContextReadDeniedException();
        }
        if (!isValidDecision(current, request) || !sameAuthority(preliminary, current)) {
            throw new AiContextReadDeniedException();
        }
        AiDomainContextBuilder builder = builders.get(current.domain());
        if (builder == null) {
            throw new AiContextReadDeniedException();
        }
        TrustedContextInput input = new TrustedContextInput(current, current.acceptedActorId(), requestId,
                clock.instant());
        return new AiAuthorizedContext(requestId, current.assistantKey(), current.domain(), current.capability(),
                current.resolvedResource(), CONTEXT_VERSION, input.builtAt(),
                AiAuthorizedContext.Freshness.LIVE_READ_NO_CACHE, builder.build(input));
    }

    static boolean isValidDecision(AiCapabilityDecision decision, AiCapabilityRequest request) {
        return decision != null && request != null && decision.allowed() && decision.acceptedActorId() != null
                && decision.acceptedActorId() > 0 && decision.assistantKey() == request.assistantKey()
                && decision.selectedSystemRole() != null
                && decision.capability() == request.capability() && decision.domain() == decision.capability().domain()
                && decision.riskBoundary() == decision.capability().riskBoundary()
                && decision.resolvedResource() != null
                && decision.resolvedResource().type() == decision.capability().resourceType()
                && decision.denialReason() == null
                && decision.decisionReason() == AiCapabilityDecisionReason.ALLOWED_BY_EFFECTIVE_PERMISSION;
    }

    private static boolean sameAuthority(AiCapabilityDecision first, AiCapabilityDecision current) {
        return Objects.equals(first.acceptedActorId(), current.acceptedActorId())
                && first.selectedSystemRole() == current.selectedSystemRole()
                && first.assistantKey() == current.assistantKey()
                && first.domain() == current.domain()
                && first.capability() == current.capability()
                && first.riskBoundary() == current.riskBoundary()
                && Objects.equals(first.resolvedResource(), current.resolvedResource())
                && Objects.equals(first.evidence(), current.evidence())
                && Objects.equals(first.checkinGuidancePolicySnapshot(), current.checkinGuidancePolicySnapshot());
    }

    private static Map<AiAssistantDomain, AiDomainContextBuilder> validatedBuilders(
            List<AiDomainContextBuilder> supplied) {
        if (supplied == null || supplied.size() != AiAssistantDomain.values().length) {
            throw new IllegalArgumentException("exactly one context builder per domain is required");
        }
        Map<AiAssistantDomain, AiDomainContextBuilder> result = new EnumMap<>(AiAssistantDomain.class);
        for (AiDomainContextBuilder builder : supplied) {
            if (builder == null || builder.domain() == null || result.put(builder.domain(), builder) != null) {
                throw new IllegalArgumentException("context builders must be unique by domain");
            }
        }
        if (result.size() != AiAssistantDomain.values().length) {
            throw new IllegalArgumentException("all context domains require a builder");
        }
        return Map.copyOf(result);
    }
}
