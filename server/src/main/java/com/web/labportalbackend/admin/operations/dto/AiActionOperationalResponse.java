package com.web.labportalbackend.admin.operations.dto;

import com.web.labportalbackend.ai.enums.AiActionExecutionStatus;
import com.web.labportalbackend.ai.enums.AiActionSuggestionStatus;
import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.ai.enums.AiResourceType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record AiActionOperationalResponse(
        @Schema(description = "Unique AI action suggestion identifier") Long id,
        @Schema(description = "User who requested the action") Long requestedById,
        @Schema(description = "Assistant that produced the action") AiAssistantKey assistantKey,
        @Schema(description = "Canonical action identifier") String actionType,
        @Schema(description = "Resource type targeted by the action") AiResourceType resourceType,
        @Schema(description = "Resource identifier targeted by the action") Long resourceId,
        @Schema(description = "Human review status of the suggestion") AiActionSuggestionStatus status,
        @Schema(description = "Execution outcome of the action") AiActionExecutionStatus executionStatus,
        @Schema(description = "Time when the suggestion was created") Instant createdAt
) {
}
