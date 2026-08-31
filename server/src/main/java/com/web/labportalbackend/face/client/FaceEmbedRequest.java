package com.web.labportalbackend.face.client;

import io.swagger.v3.oas.annotations.media.Schema;

public record FaceEmbedRequest(
        @Schema(description = "Base64-encoded JPEG or PNG image bytes") String imageBase64,
        @Schema(description = "Validated image media type") String contentType,
        @Schema(description = "Whether the face service must run liveness checks") boolean livenessRequired
) {
}
