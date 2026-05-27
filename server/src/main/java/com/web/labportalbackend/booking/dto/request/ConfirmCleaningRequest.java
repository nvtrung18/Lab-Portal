package com.web.labportalbackend.booking.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Setter;

@Setter
public class ConfirmCleaningRequest {

    private Long cleaningId;

    private Long taskId;

    @NotNull(message = "Cleaning ID is required")
    public Long getCleaningId() {
        return cleaningId != null ? cleaningId : taskId;
    }
}
