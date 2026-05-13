package com.web.labportalbackend.research.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
@Schema(description = "Submitted final research product")
public class ProductResponse {
    @Schema(description = "Product ID", example = "1")
    private Long id;

    @JsonProperty("project_id")
    @Schema(description = "Research project ID", example = "10")
    private Long projectId;

    @Schema(description = "Product name", example = "Final Demo Package")
    private String name;

    @JsonProperty("file_url")
    @Schema(description = "Stored product file URL", example = "local://research-products/10/1715660000000-final.zip")
    private String fileUrl;

    @JsonProperty("created_at")
    @Schema(description = "Submission timestamp")
    private Instant createdAt;
}
