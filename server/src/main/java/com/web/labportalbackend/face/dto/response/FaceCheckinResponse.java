package com.web.labportalbackend.face.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record FaceCheckinResponse(
        @Schema(description = "Booking evaluated for face check-in") Long bookingId,
        @Schema(description = "Whether Spring committed the booking check-in") boolean checkedIn,
        @Schema(description = "MATCH or the machine-readable failure result") String result,
        @Schema(description = "Face-match confidence from zero to one") Double confidenceScore,
        @Schema(description = "Liveness score from zero to one") Double livenessScore,
        @Schema(description = "Machine-readable failure reason") String failureReason,
        @Schema(description = "Committed check-in time, or null when check-in failed") Instant checkedInAt
) {
}
