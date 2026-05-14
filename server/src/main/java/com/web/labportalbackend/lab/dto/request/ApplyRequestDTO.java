package com.web.labportalbackend.lab.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter @Setter @Builder @AllArgsConstructor
@Schema(description = "Request body for applying to a laboratory")
public class ApplyRequestDTO {
    @NotNull(message = "User ID is required") @Schema(description = "User ID of the applicant", example = "5") private Long userId;
    @NotBlank(message = "CV URL is required") @Schema(description = "CV file URL", example = "https://storage.example.com/cvs/user5_cv.pdf") private String cvUrl;
}
