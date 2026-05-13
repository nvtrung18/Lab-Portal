package com.web.labportalbackend.booking.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Request DTO for check-in operation.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckinRequest {

    @NotNull(message = "booking_id is required")
    @JsonProperty("booking_id")
    private Long bookingId;
}
