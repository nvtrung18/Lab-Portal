package com.web.labportalbackend.ai.client;

public record AiGatewayFailure(AiGatewayFailureCategory category, Integer statusCode, String errorCode,
                               String requestId) {

    public AiGatewayFailure(AiGatewayFailureCategory category, Integer statusCode, String errorCode) {
        this(category, statusCode, errorCode, null);
    }

    public AiGatewayFailure {
        if (category == null) {
            throw new IllegalArgumentException("category is required");
        }
        if (statusCode != null && (statusCode < 100 || statusCode > 599)) {
            throw new IllegalArgumentException("statusCode must be an HTTP status code");
        }
        if (requestId != null && requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
    }
}
