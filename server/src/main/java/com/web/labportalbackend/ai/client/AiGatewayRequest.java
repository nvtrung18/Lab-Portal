package com.web.labportalbackend.ai.client;

import com.fasterxml.jackson.databind.JsonNode;

public record AiGatewayRequest(JsonNode payload, String requestId) {

    public AiGatewayRequest {
        if (payload == null) {
            throw new IllegalArgumentException("AI gateway payload is required");
        }
        payload = payload.deepCopy();
        requestId = requestId == null || requestId.isBlank() ? null : requestId.trim();
    }

    @Override
    public JsonNode payload() {
        return payload.deepCopy();
    }
}
