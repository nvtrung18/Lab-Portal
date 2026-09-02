package com.web.labportalbackend.face.dto.response;

import com.web.labportalbackend.common.enums.BookingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record FaceCheckinCandidateResponse(
        @Schema(description = "Approved booking eligible for manager-operated face check-in") Long bookingId,
        @Schema(description = "Student who owns the booking and registered face profile") Long userId,
        @Schema(description = "Student display name") String studentName,
        @Schema(description = "Student email address") String studentEmail,
        @Schema(description = "Laboratory managed by the authenticated manager") Long labId,
        @Schema(description = "Laboratory display name") String labName,
        @Schema(description = "Registered laboratory time slot") Long slotId,
        @Schema(description = "Start of the registered laboratory session") Instant startTime,
        @Schema(description = "End of the registered laboratory session") Instant endTime,
        @Schema(description = "Current booking lifecycle state") BookingStatus status
) {
}
