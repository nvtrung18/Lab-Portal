package com.web.labportalbackend.admin.website.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record WebsiteSaveRequest(
        @Schema(description = "Revision returned by the latest administration read") @NotNull @Min(0) Long revision,
        @Schema(description = "Complete website content including deliberately empty fields") @NotNull @Valid WebsiteContent content
) {}
