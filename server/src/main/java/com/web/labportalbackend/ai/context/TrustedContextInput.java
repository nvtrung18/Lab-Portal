package com.web.labportalbackend.ai.context;

import com.web.labportalbackend.ai.service.AiCapabilityDecision;
import java.time.Instant;

/** Builder input created only from the fresh resolver decision. */
public record TrustedContextInput(
        AiCapabilityDecision decision,
        Long actorId,
        String requestId,
        Instant builtAt) {

    public TrustedContextInput {
        if (decision == null || !decision.allowed() || actorId == null || actorId <= 0
                || !actorId.equals(decision.acceptedActorId()) || decision.selectedSystemRole() == null
                || builtAt == null) {
            throw new IllegalArgumentException("trusted context input is invalid");
        }
    }
}
