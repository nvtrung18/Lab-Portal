package com.web.labportalbackend.booking.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ReviewComplaintRequest {
    @NotBlank(message = "Decision is required")
    private String decision;

    @Size(max = 1000, message = "Note must not exceed 1000 characters")
    private String note;
}
