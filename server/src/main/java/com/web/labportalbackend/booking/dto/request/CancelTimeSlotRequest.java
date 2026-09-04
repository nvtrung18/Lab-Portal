package com.web.labportalbackend.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CancelTimeSlotRequest {

    @Schema(description = "Optional reason communicated to registered users when the manager cancels the time slot")
    private String reason;
}
