package com.web.labportalbackend.research.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Schema(description = "Request body for evaluating a research project")
public class EvaluationRequest {

    @NotNull(message = "Project ID is required")
    @JsonProperty("project_id")
    @Schema(description = "Research project ID", example = "1")
    private Long projectId;

    @NotNull(message = "Reviewer ID is required")
    @JsonProperty("reviewer_id")
    @Schema(description = "Reviewer user ID", example = "5")
    private Long reviewerId;

    @NotNull(message = "Score is required")
    @DecimalMin(value = "0.0", message = "Score must be at least 0.0")
    @DecimalMax(value = "100.0", message = "Score must not exceed 100.0")
    @Schema(description = "Evaluation score from 0.0 to 100.0", example = "85.5", minimum = "0.0", maximum = "100.0")
    private BigDecimal score;

    @Size(max = 5000, message = "Comments must not exceed 5000 characters")
    @Schema(description = "Reviewer comments", example = "Strong implementation and clear final report.")
    private String comments;
}
