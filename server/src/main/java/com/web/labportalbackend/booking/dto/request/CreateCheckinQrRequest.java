package com.web.labportalbackend.booking.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateCheckinQrRequest {
    @NotNull(message = "Vui lòng chọn đăng ký sử dụng PTN.")
    private Long bookingId;
}
