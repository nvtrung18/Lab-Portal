package com.web.labportalbackend.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO for CV application requests.
 * Contains the CV URL for a new application.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@Schema(description = "Request body for applying to a laboratory")
public class ApplyRequestDTO {

    @NotNull(message = "User ID is required")
    @Schema(description = "User ID of the applicant", example = "5")
    private Long userId;

    @NotBlank(message = "CV URL is required")
    @Schema(description = "URL/path to the CV file", example = "https://storage.example.com/cvs/user5_cv.pdf")
    private String cvUrl;
}
