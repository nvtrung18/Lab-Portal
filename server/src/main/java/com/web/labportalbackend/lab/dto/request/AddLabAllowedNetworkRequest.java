package com.web.labportalbackend.lab.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "An IP literal or CIDR network allowed for laboratory self check-in")
public record AddLabAllowedNetworkRequest(
        @NotBlank
        @Size(max = 100)
        @Schema(description = "Public IP address or CIDR network", requiredMode = Schema.RequiredMode.REQUIRED)
        String network
) {
}
