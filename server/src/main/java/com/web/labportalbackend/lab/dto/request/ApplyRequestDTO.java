package com.web.labportalbackend.lab.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter @Setter @Builder @AllArgsConstructor
@Schema(description = "Request body for applying to a laboratory")
public class ApplyRequestDTO {
    @NotBlank(message = "CV URL is required") @Schema(description = "CV file URL", example = "https://storage.example.com/cvs/user5_cv.pdf") private String cvUrl;
}
