package com.web.labportalbackend.ai.client;

public record AiGatewayErrorResponse(String errorCode, String message, boolean retryable) {
}
