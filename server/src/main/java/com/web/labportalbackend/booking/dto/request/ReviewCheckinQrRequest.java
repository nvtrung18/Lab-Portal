package com.web.labportalbackend.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record ReviewCheckinQrRequest(
        @Schema(description = "Whether the lab manager approves the student's QR fallback request", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Vui lòng chọn duyệt hoặc từ chối yêu cầu QR.")
        Boolean approved
) {
}
