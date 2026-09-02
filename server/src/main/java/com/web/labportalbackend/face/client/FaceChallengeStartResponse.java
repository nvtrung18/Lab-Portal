package com.web.labportalbackend.face.client;

import io.swagger.v3.oas.annotations.media.Schema;

public record FaceChallengeStartResponse(
        @Schema(description = "Signed challenge token returned by face-service") String challengeToken,
        @Schema(description = "Required movement shown to the user") String action,
        @Schema(description = "Epoch second at which the challenge expires") long expiresAt
) {
}
