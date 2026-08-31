package com.web.labportalbackend.admin.operations.dto;

import com.web.labportalbackend.ai.enums.AiAssistantKey;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record AiUsageOperationalResponse(
        @Schema(description = "Unique AI usage log identifier") Long id,
        @Schema(description = "User attributed to the AI request") Long userId,
        @Schema(description = "Assistant that handled the request") AiAssistantKey assistantKey,
        @Schema(description = "Application module attributed to the request") String module,
        @Schema(description = "Laboratory scope when the request was lab-scoped") Long labId,
        @Schema(description = "Project scope when the request was project-scoped") Long projectId,
        @Schema(description = "Research group scope when the request was group-scoped") Long groupId,
        @Schema(description = "Prompt token count recorded for the request") int promptTokens,
        @Schema(description = "Completion token count recorded for the request") int completionTokens,
        @Schema(description = "Recorded request outcome") String status,
        @Schema(description = "Whether an error was recorded without exposing its raw text") boolean errorRecorded,
        @Schema(description = "Time when the usage was recorded") Instant createdAt
) {
}
