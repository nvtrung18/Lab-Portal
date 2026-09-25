package com.web.labportalbackend.lab.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

@Schema(description = "Laboratory location and policy used for student self check-in")
public record LabCheckinPolicyRequest(
        @NotNull
        @DecimalMin(value = "-90.0")
        @DecimalMax(value = "90.0")
        @Schema(description = "Latitude of the laboratory check-in point", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal latitude,

        @NotNull
        @DecimalMin(value = "-180.0")
        @DecimalMax(value = "180.0")
        @Schema(description = "Longitude of the laboratory check-in point", requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal longitude,

        @NotNull
        @Positive
        @Schema(description = "Maximum allowed distance from the laboratory point in meters",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Integer radiusMeters,

        @NotNull
        @Positive
        @Schema(description = "Maximum accepted GPS accuracy in meters", requiredMode = Schema.RequiredMode.REQUIRED)
        Integer maxAccuracyMeters,

        @NotNull
        @Schema(description = "Whether student self check-in is enabled for this laboratory",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean enabled
) {
}
