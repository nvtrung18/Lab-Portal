package com.web.labportalbackend.ai.client;

import com.fasterxml.jackson.databind.JsonNode;

public record AiToolPlanningResponse(
        AiToolPlanningDecision decision,
        String message,
        JsonNode toolRequest,
        int promptTokens,
        int completionTokens
) {
    public AiToolPlanningResponse {
        if (decision == null || promptTokens < 0 || completionTokens < 0) {
            throw new IllegalArgumentException("AI tool planning response is invalid");
        }
        if (decision == AiToolPlanningDecision.TOOL_REQUEST) {
            if (toolRequest == null || !toolRequest.isObject() || message != null) {
                throw new IllegalArgumentException("AI tool request decision is invalid");
            }
            toolRequest = toolRequest.deepCopy();
        } else if (toolRequest != null || message == null || message.isBlank()) {
            throw new IllegalArgumentException("AI non-tool decision is invalid");
        }
    }
}
