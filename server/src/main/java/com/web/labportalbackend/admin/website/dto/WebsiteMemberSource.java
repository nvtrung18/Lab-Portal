package com.web.labportalbackend.admin.website.dto;

import com.web.labportalbackend.common.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record WebsiteMemberSource(
        @Schema(description = "Account identifier for linking a public member profile") Long id,
        @Schema(description = "Account full name; may be null") String fullName,
        @Schema(description = "Account username for identification in the admin picker") String username,
        @Schema(description = "Account email for admin identification; not automatically published") String email,
        @Schema(description = "Current account status; does not determine website visibility") UserStatus status
) {}
