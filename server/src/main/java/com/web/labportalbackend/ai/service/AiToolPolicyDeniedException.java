package com.web.labportalbackend.ai.service;

/** Typed, fail-closed denial that intentionally carries no resource or model details. */
public final class AiToolPolicyDeniedException extends RuntimeException {
    private final AiToolPolicyDenialReason reason;

    public AiToolPolicyDeniedException(AiToolPolicyDenialReason reason) {
        super(reason == null ? AiToolPolicyDenialReason.MALFORMED_DECISION.name() : reason.name());
        this.reason = reason == null ? AiToolPolicyDenialReason.MALFORMED_DECISION : reason;
    }

    public AiToolPolicyDenialReason reason() {
        return reason;
    }
}
