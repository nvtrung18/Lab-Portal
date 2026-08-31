package com.web.labportalbackend.face.client;

import io.swagger.v3.oas.annotations.media.Schema;

public record FaceMatchResponse(
        @Schema(description = "MATCH or a machine-readable face failure result") String result,
        @Schema(description = "Face-match confidence from zero to one") Double confidenceScore,
        @Schema(description = "Liveness score from zero to one") Double livenessScore,
        @Schema(description = "Machine-readable processing failure reason") String failureReason
) {
}
