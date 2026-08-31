package com.web.labportalbackend.face.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

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
        boolean livenessRequired
) {
}
