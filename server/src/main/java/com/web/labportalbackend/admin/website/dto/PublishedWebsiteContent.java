package com.web.labportalbackend.admin.website.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record PublishedWebsiteContent(
        @Schema(description = "Visible published content; null means use the supplied demonstration template") WebsiteContent content,
        @Schema(description = "Publication time; null before the first publication") Instant publishedAt
) {}
