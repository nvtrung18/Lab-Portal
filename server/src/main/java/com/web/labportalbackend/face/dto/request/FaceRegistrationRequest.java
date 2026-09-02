package com.web.labportalbackend.face.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record FaceRegistrationRequest(
        @NotBlank
        @Size(max = 14_000_000)
        @Schema(description = "Base64-encoded JPEG or PNG face image")
        String imageBase64,
        @NotBlank
        @Pattern(regexp = "image/(jpeg|png)")
        @Schema(description = "Image media type", allowableValues = {"image/jpeg", "image/png"})
        String contentType,
        @Schema(description = "Whether liveness validation is required for registration")
        boolean livenessRequired,
        @NotNull
        @Size(min = 2, max = 2)
        @Schema(description = "Live-camera face samples captured in left then right order")
        List<@Valid FaceChallengeFrameRequest> sideImages,
        @Size(max = 8)
        @Schema(description = "Additional camera frames captured during the signed active-liveness challenge")
        List<@Valid FaceChallengeFrameRequest> challengeFrames,
        @Size(max = 4096)
        @Schema(description = "Signed active-liveness challenge token")
        String challengeToken
) {
    public FaceRegistrationRequest(String imageBase64, String contentType, boolean livenessRequired) {
        this(imageBase64, contentType, livenessRequired, List.of(), List.of(), null);
    }
}
