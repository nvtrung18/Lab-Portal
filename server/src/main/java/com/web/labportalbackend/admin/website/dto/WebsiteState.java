package com.web.labportalbackend.admin.website.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record WebsiteState(
        @Schema(description = "Revision required when saving or publishing") long revision,
        @Schema(description = "Latest saved draft; null before the first save") WebsiteContent draft,
        @Schema(description = "Published content; null before the first publication") WebsiteContent published,
        @Schema(description = "Last publication time; null before the first publication") Instant publishedAt
) {}
