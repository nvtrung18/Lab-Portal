package com.web.labportalbackend.booking.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssignCleaningRequest {

    @NotNull(message = "Cleaning ID is required")
    private Long cleaningId;

    @NotNull(message = "Staff ID is required")
    private Long staffId;
}
