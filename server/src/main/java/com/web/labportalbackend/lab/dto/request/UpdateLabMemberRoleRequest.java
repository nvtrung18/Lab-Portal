package com.web.labportalbackend.lab.dto.request;

import com.web.labportalbackend.lab.enums.LabMemberRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Change an active student member's role within a laboratory")
public record UpdateLabMemberRoleRequest(
        @NotNull(message = "Laboratory member role is required")
        @Schema(description = "Role assigned within this laboratory",
                allowableValues = {"MEMBER", "DEPUTY", "SECRETARY"},
                requiredMode = Schema.RequiredMode.REQUIRED)
        LabMemberRole role
) {
}
