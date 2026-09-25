package com.web.labportalbackend.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

@Schema(description = "Student GPS evidence submitted for self check-in")
public record StudentSelfCheckinRequest(
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
        BigDecimal accuracyMeters
) {
}
