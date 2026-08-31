package com.web.labportalbackend.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CheckinRequest {

    @Schema(description = "Short-lived QR fallback token generated for an approved booking", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Vui lòng cung cấp token check-in.")
    private String token;
}
