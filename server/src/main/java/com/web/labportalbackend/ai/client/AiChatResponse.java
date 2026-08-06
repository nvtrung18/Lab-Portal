package com.web.labportalbackend.ai.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;

public record AiChatResponse(String assistantKey, String answer, int promptTokens, int completionTokens,
                             Map<String, JsonNode> metadata) {

    public AiChatResponse {
        metadata = copyMetadata(metadata);
    }

    @Override
    public Map<String, JsonNode> metadata() {
        return copyMetadata(metadata);
    }

    private static Map<String, JsonNode> copyMetadata(Map<String, JsonNode> source) {
        if (source == null) {
            throw new IllegalArgumentException("metadata is required");
        }
        Map<String, JsonNode> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, value == null ? null : value.deepCopy()));
        return Map.copyOf(copy);
    }
}
