package com.web.labportalbackend.face.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Student FaceID, GPS and camera-liveness evidence for self check-in")
public record StudentFaceCheckinRequest(
        @NotNull
        @Positive
        @Schema(description = "Approved booking to check in", requiredMode = Schema.RequiredMode.REQUIRED)
        Long bookingId,

        @NotNull
        @DecimalMin(value = "-90.0")
        @DecimalMax(value = "90.0")
        @Schema(description = "Latitude reported by the browser", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal latitude,

        @NotNull
        @DecimalMin(value = "-180.0")
        @DecimalMax(value = "180.0")
        @Schema(description = "Longitude reported by the browser", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal longitude,

        @NotNull
        @DecimalMin(value = "0.0")
        @Schema(description = "Browser-reported GPS accuracy in meters", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal accuracyMeters,

        @NotBlank
        @Size(max = 14_000_000)
        @Schema(description = "Base64-encoded JPEG or PNG live face image", requiredMode = Schema.RequiredMode.REQUIRED)
        String imageBase64,

        @NotBlank
        @Pattern(regexp = "image/(jpeg|png)")
        @Schema(description = "Image media type", allowableValues = {"image/jpeg", "image/png"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        String contentType,

        @NotNull
        @Size(min = 4, max = 8)
        @Schema(description = "Consecutive live-camera frames captured while the student remains stable",
                requiredMode = Schema.RequiredMode.REQUIRED)
        List<@Valid FaceChallengeFrameRequest> challengeFrames,

        @NotBlank
        @Size(max = 4096)
        @Schema(description = "Signed passive-observation token issued for this camera session",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String challengeToken
) {
}
