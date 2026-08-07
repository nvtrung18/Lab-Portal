package com.web.labportalbackend.ai.service;

public final class AiCapabilityDeniedException extends RuntimeException {

    private final AiCapabilityDecision decision;

    public AiCapabilityDeniedException(AiCapabilityDecision decision) {
        super(decision.denialReason().name());
        this.decision = decision;
    }

    public AiCapabilityDecision decision() {
        return decision;
    }

    public AiCapabilityDecision getDecision() {
        return decision;
    }
}
