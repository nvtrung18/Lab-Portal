package com.web.labportalbackend.booking.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConfirmCleaningRequest {

    @NotNull(message = "Cleaning ID is required")
    private Long cleaningId;
}
