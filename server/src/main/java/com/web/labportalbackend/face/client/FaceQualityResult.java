package com.web.labportalbackend.face.client;

import io.swagger.v3.oas.annotations.media.Schema;

public record FaceQualityResult(
        @Schema(description = "Whether the submitted image passed face-quality checks") boolean passed,
        @Schema(description = "Machine-readable quality failure reason, or null when quality passed") String reason
) {
}
