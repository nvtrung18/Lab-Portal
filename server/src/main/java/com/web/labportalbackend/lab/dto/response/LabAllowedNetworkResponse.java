package com.web.labportalbackend.lab.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Laboratory self check-in allowlisted network")
public record LabAllowedNetworkResponse(
        @Schema(description = "Allowlist entry ID") Long id,
        @Schema(description = "Normalized IP literal or CIDR network") String network,
        @Schema(description = "Whether the entry can currently authorize self check-in") boolean active,
        @Schema(description = "Time the entry was registered") Instant createdAt,
        @Schema(description = "Time the entry was last updated") Instant updatedAt
) {
}
