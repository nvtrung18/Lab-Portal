package com.web.labportalbackend.ai.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record AiActionResultResponse(
        @Schema(description = "Action suggestion that was confirmed or cancelled")
        Long suggestionId,
        @Schema(description = "Domain action represented by the suggestion")
        String actionType,
        @Schema(description = "Final action state")
        String status,
        @Schema(description = "Identifier created by the domain service; absent for cancellation", nullable = true)
        Long targetId) {
}
