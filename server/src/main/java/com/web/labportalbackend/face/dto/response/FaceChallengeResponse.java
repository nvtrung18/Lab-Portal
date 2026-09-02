package com.web.labportalbackend.face.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record FaceChallengeResponse(
        @Schema(description = "Signed active-liveness challenge token") String challengeToken,
        @Schema(description = "Movement the user must perform") String action,
        @Schema(description = "Epoch second at which the challenge expires") long expiresAt
) {
}
