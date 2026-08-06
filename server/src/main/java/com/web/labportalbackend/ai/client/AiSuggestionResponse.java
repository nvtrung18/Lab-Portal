package com.web.labportalbackend.ai.client;

import com.fasterxml.jackson.databind.JsonNode;

public record AiSuggestionResponse(String assistantKey, String actionType, int schemaVersion, JsonNode payload,
                                   double confidence, String explanation) {

    public AiSuggestionResponse {
        if (payload == null) {
            throw new IllegalArgumentException("payload is required");
        }
        payload = payload.deepCopy();
    }

    @Override
    public JsonNode payload() {
        return payload.deepCopy();
    }
}
