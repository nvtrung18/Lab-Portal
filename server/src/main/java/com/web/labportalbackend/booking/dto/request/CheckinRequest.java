package com.web.labportalbackend.booking.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CheckinRequest {
    @NotBlank(message = "Vui lòng cung cấp token check-in.")
    private String token;
}
