package com.web.labportalbackend.ai.dto.response;

public record AiAssistantChatResponse(
        String assistantKey,
        String answer,
        int promptTokens,
        int completionTokens) {
}
