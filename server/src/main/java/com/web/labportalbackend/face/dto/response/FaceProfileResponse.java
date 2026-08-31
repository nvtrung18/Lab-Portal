package com.web.labportalbackend.face.dto.response;

import com.web.labportalbackend.face.enums.FaceProfileStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record FaceProfileResponse(
        @Schema(description = "User who owns the face profile") Long userId,
        @Schema(description = "Current face profile lifecycle state") FaceProfileStatus status,
        @Schema(description = "Face model that produced the encrypted embedding") String embeddingModel,
        @Schema(description = "Time the face profile was last updated") Instant updatedAt
) {
}
