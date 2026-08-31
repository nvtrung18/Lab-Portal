package com.web.labportalbackend.face.dto.request;

import com.web.labportalbackend.face.enums.FaceConsentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record FaceConsentRequest(
        @NotNull
        @Schema(description = "Requested consent state; only GRANTED or WITHDRAWN is accepted")
        FaceConsentStatus status,
        @Size(max = 1000)
        @Schema(description = "Optional reason recorded with the consent change")
        String reason
) {
}
