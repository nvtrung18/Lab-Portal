package com.web.labportalbackend.face.client;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record FaceMatchRequest(
        @Schema(description = "Base64-encoded JPEG or PNG check-in image") String imageBase64,
        @Schema(description = "Validated image media type") String contentType,
        @Schema(description = "Decrypted reference embedding held only in Spring process memory")
        List<Double> referenceEmbedding,
        @Schema(description = "Configured minimum face-match confidence") double confidenceThreshold,
        @Schema(description = "Configured minimum liveness score") double livenessThreshold,
        @Schema(description = "Whether liveness must pass") boolean livenessRequired
) {
}
