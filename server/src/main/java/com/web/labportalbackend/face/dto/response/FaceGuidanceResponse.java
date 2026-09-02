package com.web.labportalbackend.face.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record FaceGuidanceResponse(
        @Schema(description = "Number of faces detected in the current camera frame") int detectedFaces,
        @Schema(description = "Whether exactly one face is present") boolean singleFace,
        @Schema(description = "Whether the face position and size fit the capture guide") boolean faceInGuide,
        @Schema(description = "Whether facial landmarks indicate a near-frontal pose") boolean facingForward,
        @Schema(description = "Whether required eye, nose, and mouth landmarks are visible") boolean landmarksVisible,
        @Schema(description = "Whether face-region brightness is within the accepted range") boolean lightingGood,
        @Schema(description = "Whether the face region is sufficiently sharp") boolean sharpnessGood,
        @Schema(description = "Normalized horizontal center of the detected face") Double centerX,
        @Schema(description = "Normalized vertical center of the detected face") Double centerY,
        @Schema(description = "Detected face width divided by frame width") Double faceWidthRatio,
        @Schema(description = "Detected face height divided by frame height") Double faceHeightRatio,
        @Schema(description = "Machine-readable reason for the first failed guidance check") String failureReason
) {
}
