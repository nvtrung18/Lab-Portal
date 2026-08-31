package com.web.labportalbackend.face.dto.response;

import com.web.labportalbackend.face.enums.FaceConsentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record FaceConsentResponse(
        @Schema(description = "User whose face consent state is returned") Long userId,
        @Schema(description = "Current face consent state") FaceConsentStatus status,
        @Schema(description = "Time the current consent state was recorded") Instant changedAt
) {
}
