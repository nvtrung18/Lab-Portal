package com.web.labportalbackend.face.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record FaceCheckinRequest(
        @NotNull
        @Positive
        @Schema(description = "Approved booking owned by the authenticated student") Long bookingId,
        @NotBlank
        @Size(max = 14_000_000)
        @Schema(description = "Base64-encoded JPEG or PNG live face image") String imageBase64,
        @NotBlank
        @Pattern(regexp = "image/(jpeg|png)")
        @Schema(description = "Image media type", allowableValues = {"image/jpeg", "image/png"}) String contentType
) {
}
