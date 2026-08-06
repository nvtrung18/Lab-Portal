package com.web.labportalbackend.ai.client;

public record AiGatewayFailure(AiGatewayFailureCategory category, Integer statusCode, String errorCode) {

    public AiGatewayFailure {
        if (category == null) {
            throw new IllegalArgumentException("category is required");
        }
        if (statusCode != null && (statusCode < 100 || statusCode > 599)) {
            throw new IllegalArgumentException("statusCode must be an HTTP status code");
        }
    }
}
