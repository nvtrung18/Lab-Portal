package com.web.labportalbackend.booking.dto.response;

import com.web.labportalbackend.face.enums.FaceFallbackReason;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record CheckinQrRequestResponse(
        @Schema(description = "Ephemeral QR request identifier") String requestId,
        @Schema(description = "Booking awaiting QR fallback approval") Long bookingId,
        @Schema(description = "Student account that submitted the request") Long studentId,
        @Schema(description = "Student display name") String studentName,
        @Schema(description = "Predefined QR fallback reason") FaceFallbackReason fallbackReason,
        @Schema(description = "Reason text shown to the lab manager") String reason,
        @Schema(description = "Request status", allowableValues = {"PENDING", "APPROVED", "REJECTED"}) String status,
        @Schema(description = "Time at which the request and any approved QR expire") Instant expiresAt
) {
}
