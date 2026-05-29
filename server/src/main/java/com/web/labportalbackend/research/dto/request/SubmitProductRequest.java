package com.web.labportalbackend.research.dto.request;

import com.web.labportalbackend.research.enums.ProductType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(description = "Research product submission form")
public class SubmitProductRequest {

    @NotNull(message = "Project ID is required")
    @Schema(description = "Research project ID", example = "10")
    private Long projectId;

    @Schema(description = "Optional research group ID", example = "3")
    private Long groupId;

    @NotNull(message = "Product type is required")
    @Schema(description = "Product type", example = "SOURCE_CODE")
    private ProductType productType;

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must not exceed 200 characters")
    @Schema(description = "Product title", example = "Source code demo nhận diện khuôn mặt")
    private String title;

    @Size(max = 4000, message = "Description must not exceed 4000 characters")
    @Schema(description = "Product description")
    private String description;

    @Size(max = 1000, message = "External link must not exceed 1000 characters")
    @Schema(description = "Optional external artifact link", example = "https://github.com/example/demo")
    private String externalLink;
}
