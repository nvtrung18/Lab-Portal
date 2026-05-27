package com.web.labportalbackend.booking.dto.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CancelTimeSlotRequest {
    private String reason;
    private Boolean notifyByEmail = true;
}
