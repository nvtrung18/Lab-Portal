package com.web.labportalbackend.face.client;

import io.swagger.v3.oas.annotations.media.Schema;

public record FaceMatchResponse(
        @Schema(description = "MATCH or a machine-readable face failure result") String result,
        @Schema(description = "Face-match confidence from zero to one") Double confidenceScore,
        @Schema(description = "Liveness score from zero to one") Double livenessScore,
        @Schema(description = "Whether the signed active-liveness challenge passed") boolean activeLivenessPassed,
        @Schema(description = "Whether the signed passive camera observation passed") boolean passiveLivenessPassed,
        @Schema(description = "Machine-readable processing failure reason") String failureReason
) {
    public FaceMatchResponse(String result, Double confidenceScore, Double livenessScore, String failureReason) {
        this(result, confidenceScore, livenessScore, false, false, failureReason);
    }

    public FaceMatchResponse(String result, Double confidenceScore, Double livenessScore,
                             boolean activeLivenessPassed, String failureReason) {
        this(result, confidenceScore, livenessScore, activeLivenessPassed, false, failureReason);
    }
}
