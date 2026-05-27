package com.web.labportalbackend.booking.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewBookingRequest {
    @NotBlank(message = "Decision is required")
    private String decision;
    private String note;
}
