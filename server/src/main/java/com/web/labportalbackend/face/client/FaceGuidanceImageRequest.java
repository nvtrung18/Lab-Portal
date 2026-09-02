package com.web.labportalbackend.face.client;

import io.swagger.v3.oas.annotations.media.Schema;

public record FaceGuidanceImageRequest(
        @Schema(description = "Base64-encoded camera frame") String imageBase64,
        @Schema(description = "Validated camera frame media type") String contentType,
        @Schema(description = "Whether liveness processing is requested for this frame") boolean livenessRequired
) {
}
