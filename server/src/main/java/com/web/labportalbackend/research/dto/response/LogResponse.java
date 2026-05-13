package com.web.labportalbackend.research.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
@Schema(description = "Research project audit log entry")
public class LogResponse {
    @Schema(description = "Log ID", example = "1")
    private Long id;

    @JsonProperty("project_id")
    @Schema(description = "Research project ID", example = "10")
    private Long projectId;

    @JsonProperty("user_id")
    @Schema(description = "Actor user ID", example = "5", nullable = true)
    private Long userId;

    @Schema(description = "Audit action", example = "SUBMIT_PRODUCT")
    private String action;

    @Schema(description = "Audit details")
    private String details;

    @JsonProperty("created_at")
    @Schema(description = "Log timestamp")
    private Instant createdAt;
}
