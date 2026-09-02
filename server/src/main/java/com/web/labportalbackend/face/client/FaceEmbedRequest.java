package com.web.labportalbackend.face.client;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record FaceEmbedRequest(
        @Schema(description = "Base64-encoded JPEG or PNG image bytes") String imageBase64,
        @Schema(description = "Validated image media type") String contentType,
        @Schema(description = "Whether the face service must run liveness checks") boolean livenessRequired,
        @Schema(description = "Additional camera frames captured during active liveness") List<FaceChallengeFrame> challengeFrames,
        @Schema(description = "Signed active-liveness challenge token") String challengeToken
) {
    public FaceEmbedRequest(String imageBase64, String contentType, boolean livenessRequired) {
        this(imageBase64, contentType, livenessRequired, List.of(), null);
    }
}
