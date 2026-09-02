package com.web.labportalbackend.face.client;

import io.swagger.v3.oas.annotations.media.Schema;

public record FaceGuidanceResult(
        @Schema(description = "Number of faces detected in the camera frame") int detectedFaces,
        @Schema(description = "Whether exactly one face is present") boolean singleFace,
        @Schema(description = "Whether face position and size fit the guide") boolean faceInGuide,
        @Schema(description = "Whether the detected pose is near frontal") boolean facingForward,
        @Schema(description = "Whether required facial landmarks are visible") boolean landmarksVisible,
        @Schema(description = "Whether face-region lighting is acceptable") boolean lightingGood,
        @Schema(description = "Whether the face region is sufficiently sharp") boolean sharpnessGood,
        @Schema(description = "Normalized horizontal face center") Double centerX,
        @Schema(description = "Normalized vertical face center") Double centerY,
        @Schema(description = "Face width relative to frame width") Double faceWidthRatio,
        @Schema(description = "Face height relative to frame height") Double faceHeightRatio,
        @Schema(description = "Machine-readable failed guidance reason") String failureReason
) {
}
