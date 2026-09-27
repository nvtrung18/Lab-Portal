package com.web.labportalbackend.research.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.web.labportalbackend.research.enums.ProductStatus;
import com.web.labportalbackend.research.enums.ProductType;
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

    @JsonProperty("group_id")
    @Schema(description = "Research group ID", example = "3")
    private Long groupId;

    @JsonProperty("submitted_by_id")
    @Schema(description = "Submitter user ID", example = "5")
    private Long submittedById;

    @Schema(description = "Display name of the submitting user")
    private String submittedByName;

    @Schema(description = "Email address of the submitting user")
    private String submittedByEmail;

    @JsonProperty("product_type")
    @Schema(description = "Product type", example = "SOURCE_CODE")
    private ProductType productType;

    @Schema(description = "Product title", example = "Source code demo nhận diện khuôn mặt")
    private String title;

    @Schema(description = "Product description")
    private String description;

    @Schema(description = "Legacy product name")
    private String name;

    @JsonProperty("file_url")
    @Schema(description = "Stored product file URL; absent for link-only submissions")
    private String fileUrl;

    @JsonProperty("file_name")
    @Schema(description = "Original name of the uploaded product file; absent for link-only submissions")
    private String fileName;

    @JsonProperty("file_type")
    @Schema(description = "Content type reported for the uploaded product file; absent for link-only submissions")
    private String fileType;

    @JsonProperty("file_size")
    @Schema(description = "Uploaded product file size in bytes; absent for link-only submissions")
    private Long fileSize;

    @JsonProperty("external_link")
    @Schema(description = "External product link, when the submission provides one")
    private String externalLink;

    @Schema(description = "Submission version", example = "1")
    private Integer version;

    @Schema(description = "Product status", example = "SUBMITTED")
    private ProductStatus status;

    @JsonProperty("submitted_at")
    @Schema(description = "Time the product was submitted")
    private Instant submittedAt;

    @JsonProperty("created_at")
    @Schema(description = "Submission timestamp")
    private Instant createdAt;
}
