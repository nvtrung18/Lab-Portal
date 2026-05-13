package com.web.labportalbackend.research.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Builder
@Schema(description = "Research project evaluation")
public class EvaluationResponse {
    @Schema(description = "Evaluation ID", example = "1")
    private Long id;

    @JsonProperty("project_id")
    @Schema(description = "Research project ID", example = "10")
    private Long projectId;

    @JsonProperty("reviewer_id")
    @Schema(description = "Reviewer user ID", example = "5")
    private Long reviewerId;

    @Schema(description = "Evaluation score", example = "85.50")
    private BigDecimal score;

    @Schema(description = "Reviewer comments")
    private String comments;

    @JsonProperty("created_at")
    @Schema(description = "Evaluation timestamp")
    private Instant createdAt;
}
