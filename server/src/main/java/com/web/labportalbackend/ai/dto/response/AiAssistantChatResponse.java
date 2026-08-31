package com.web.labportalbackend.ai.dto.response;

import com.web.labportalbackend.ai.rag.dto.response.AiRagCitationResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record AiAssistantChatResponse(
        @Schema(description = "Assistant profile that produced the answer") String assistantKey,
        @Schema(description = "Grounded assistant answer returned to the caller") String answer,
        @Schema(description = "Prompt token count reported by the AI runtime") int promptTokens,
        @Schema(description = "Completion token count reported by the AI runtime") int completionTokens,
        @Schema(description = "Authorized document chunks used as sources for the answer")
        List<AiRagCitationResponse> citations) {

    public AiAssistantChatResponse {
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
