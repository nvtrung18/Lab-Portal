package com.web.labportalbackend.lab.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

@Schema(description = "Laboratory self check-in location, GPS and network policy")
public record LabCheckinPolicyResponse(
        @Schema(description = "Laboratory ID") Long labId,
        @Schema(description = "Latitude of the check-in point") BigDecimal latitude,
        @Schema(description = "Longitude of the check-in point") BigDecimal longitude,
        @Schema(description = "Maximum allowed distance from the check-in point in meters") Integer radiusMeters,
        @Schema(description = "Maximum accepted GPS accuracy in meters") Integer maxAccuracyMeters,
        @Schema(description = "Whether student self check-in is enabled") boolean enabled,
        @Schema(description = "IP literals and CIDR networks allowed for this laboratory")
        List<LabAllowedNetworkResponse> allowedNetworks
) {
}
