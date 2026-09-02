package com.web.labportalbackend.face.client;

import io.swagger.v3.oas.annotations.media.Schema;

public record FaceChallengeFrame(
        @Schema(description = "Base64-encoded camera frame used for active liveness") String imageBase64,
        @Schema(description = "Camera frame media type", allowableValues = {"image/jpeg", "image/png"}) String contentType
) {
}
