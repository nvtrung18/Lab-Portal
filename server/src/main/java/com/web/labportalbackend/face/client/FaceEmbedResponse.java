package com.web.labportalbackend.face.client;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record FaceEmbedResponse(
        @Schema(description = "Face processing result code") String result,
        @Schema(description = "Raw embedding returned only to Spring for immediate encryption") List<Double> embedding,
        @Schema(description = "Face model identifier that produced the embedding") String embeddingModel,
        @Schema(description = "Image quality decision") FaceQualityResult quality,
        @Schema(description = "Detection confidence from zero to one") Double confidenceScore,
        @Schema(description = "Liveness score from zero to one") Double livenessScore,
        @Schema(description = "Machine-readable processing failure reason") String failureReason
) {
}
