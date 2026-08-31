package com.web.labportalbackend.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ManualCheckinRequest(
        @Schema(description = "Approved booking to check in using a manager-authorized manual override", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "Vui lòng chọn đăng ký sử dụng PTN.")
        @Positive(message = "Mã đăng ký sử dụng PTN không hợp lệ.")
        Long bookingId,

        @Schema(description = "Operational justification recorded for the manual check-in override", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Vui lòng cung cấp lý do check-in thủ công.")
        @Size(max = 1000, message = "Lý do check-in thủ công không được vượt quá 1000 ký tự.")
        String reason
) {
}
