package com.web.labportalbackend.booking.dto.response;

import com.web.labportalbackend.face.enums.FaceFallbackReason;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record CheckinQrHistoryResponse(
        @Schema(description = "Ephemeral QR request identifier") String requestId,
        @Schema(description = "Booking that owns this QR request") Long bookingId,
        @Schema(description = "Predefined QR fallback reason") FaceFallbackReason fallbackReason,
        @Schema(description = "Reason text submitted with the QR request") String reason,
        @Schema(description = "Current QR request status", allowableValues = {"PENDING", "APPROVED", "REJECTED", "USED"})
        String status,
        @Schema(description = "Approved QR token, or null while pending or rejected") String token,
        @Schema(description = "Time when the lab session ends and this QR history entry expires") Instant expiresAt
) {
}
