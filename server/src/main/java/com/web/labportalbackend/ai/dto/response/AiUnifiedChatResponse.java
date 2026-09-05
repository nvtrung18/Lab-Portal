package com.web.labportalbackend.ai.dto.response;

import com.web.labportalbackend.ai.enums.AiUnifiedChatResponseType;
import com.web.labportalbackend.ai.rag.dto.response.AiRagCitationResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record AiUnifiedChatResponse(
        @Schema(description = "Outcome that determines how the client renders this turn")
        AiUnifiedChatResponseType type,
        @Schema(description = "Server-selected assistant profile; absent when no tool was selected", nullable = true)
        String assistantKey,
        @Schema(description = "Grounded answer, clarification, or safe refusal message")
        String answer,
        @Schema(description = "Total prompt tokens used by planning and answer generation")
        int promptTokens,
        @Schema(description = "Total completion tokens used by planning and answer generation")
        int completionTokens,
        @Schema(description = "Authorized document chunks used to ground an answer")
        List<AiRagCitationResponse> citations,
        @Schema(description = "Immutable server-owned action preview; present only for ACTION_PREVIEW", nullable = true)
        AiActionPreviewResponse actionPreview,
        @Schema(description = "Final action outcome; present only for ACTION_RESULT", nullable = true)
        AiActionResultResponse actionResult) {

    public AiUnifiedChatResponse(AiUnifiedChatResponseType type, String assistantKey, String answer,
                                 int promptTokens, int completionTokens, List<AiRagCitationResponse> citations) {
        this(type, assistantKey, answer, promptTokens, completionTokens, citations, null, null);
    }

    public AiUnifiedChatResponse {
        if (type == null || answer == null || answer.isBlank() || promptTokens < 0 || completionTokens < 0) {
            throw new IllegalArgumentException("Unified chat response is invalid");
        }
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
