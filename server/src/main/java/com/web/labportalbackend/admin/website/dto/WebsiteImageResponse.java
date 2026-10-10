package com.web.labportalbackend.admin.website.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record WebsiteImageResponse(
        @Schema(description = "Public image path usable by the website") String url
) {}
