package com.web.labportalbackend.booking.dto.request;

import com.web.labportalbackend.face.enums.FaceFallbackReason;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCheckinQrRequest {

    @Schema(description = "Approved booking for which the student requests QR fallback check-in", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Vui lòng chọn đăng ký sử dụng PTN.")
    private Long bookingId;

    @Schema(description = "Verified operational condition requiring QR fallback", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "Vui lòng cung cấp lý do sử dụng QR fallback.")
    private FaceFallbackReason fallbackReason;

    @Schema(description = "Student explanation when fallbackReason is OTHER")
    @Size(max = 1000, message = "Lý do khác không được vượt quá 1000 ký tự.")
    private String customReason;
}
