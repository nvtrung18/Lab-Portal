package com.web.labportalbackend.face.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FaceChallengeFrameRequest(
        @NotBlank
        @Size(max = 14_000_000)
        @Schema(description = "Base64-encoded camera frame used for active liveness") String imageBase64,
        @NotBlank
        @Pattern(regexp = "image/(jpeg|png)")
        @Schema(description = "Camera frame media type", allowableValues = {"image/jpeg", "image/png"}) String contentType
) {
}
