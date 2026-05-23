package com.web.labportalbackend.booking.dto.request;

import com.web.labportalbackend.common.enums.PenaltyType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreatePenaltyRequest {

    @NotNull(message = "Student ID is required")
    private Long userId;

    @NotNull(message = "Slot ID is required")
    private Long slotId;

    private Long bookingId;

    @NotNull(message = "Penalty type is required")
    private PenaltyType type;

    @Min(value = 0, message = "Penalty point must be greater than or equal to 0")
    private Integer point;

    @NotBlank(message = "Penalty reason is required")
    @Size(min = 10, max = 1000, message = "Penalty reason must be between 10 and 1000 characters")
    private String reason;
}
