package com.web.labportalbackend.ai.client;

public record AiGatewayErrorResponse(String errorCode, String message, boolean retryable, String requestId) {

    public AiGatewayErrorResponse(String errorCode, String message, boolean retryable) {
        this(errorCode, message, retryable, null);
    }
}
