package com.web.labportalbackend.booking.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CheckInRequest {
    @NotBlank(message = "Token is required")
    private String token;
}
