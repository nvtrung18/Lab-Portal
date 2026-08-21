package com.web.labportalbackend.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.regex.Pattern;

public record AiGatewayRequest(JsonNode payload, String requestId) {

    private static final Pattern REQUEST_ID_PATTERN =
            Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$");

    public AiGatewayRequest {
        if (payload == null) {
            throw new IllegalArgumentException("AI gateway payload is required");
        }
        payload = payload.deepCopy();
        requestId = normalizeRequestId(requestId);
    }

    @Override
    public JsonNode payload() {
        return payload.deepCopy();
    }

    public static String normalizeRequestId(String requestId) {
        String normalized = requestId == null || requestId.isBlank() ? null : requestId.trim();
        if (normalized != null && !REQUEST_ID_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Request ID is invalid");
        }
        return normalized;
    }
}
