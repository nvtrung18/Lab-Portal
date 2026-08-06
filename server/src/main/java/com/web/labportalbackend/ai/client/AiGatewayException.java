package com.web.labportalbackend.ai.client;

public final class AiGatewayException extends RuntimeException {

    private final AiGatewayFailure failure;
    private final boolean retryable;

    AiGatewayException(AiGatewayFailure failure, boolean retryable) {
        super("AI gateway request failed: " + failure.category());
        this.failure = failure;
        this.retryable = retryable;
    }

    public AiGatewayFailure failure() {
        return failure;
    }

    boolean retryable() {
        return retryable;
    }
}
