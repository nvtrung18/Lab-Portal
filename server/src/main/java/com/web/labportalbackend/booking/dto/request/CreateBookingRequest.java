package com.web.labportalbackend.booking.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a booking.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBookingRequest {
    @NotNull(message = "Time slot ID is required")
    @JsonProperty("slotId") @JsonAlias("slot_id") private Long slotId;
    @JsonProperty("purpose") private String purpose;
    @JsonProperty("participantsCount") @JsonAlias("participants_count") private Integer participantsCount = 1;
}
