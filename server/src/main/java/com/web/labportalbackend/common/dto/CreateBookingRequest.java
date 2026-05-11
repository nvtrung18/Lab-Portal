package com.web.labportalbackend.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a booking.
 * Includes validation annotations to ensure data integrity.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBookingRequest {

    @NotNull(message = "Time slot ID is required")
    @JsonProperty("slot_id")
    private Long slotId;

    @JsonProperty("purpose")
    private String purpose;

    @JsonProperty("participants_count")
    private Integer participantsCount = 1;
}
