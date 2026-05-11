package com.web.labportalbackend.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.web.labportalbackend.common.enums.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for booking data.
 * Returns safe, public-facing information about a booking.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("user_id")
    private Long userId;

    @JsonProperty("lab_id")
    private Long labId;

    @JsonProperty("slot_id")
    private Long slotId;

    @JsonProperty("start_time")
    private Instant startTime;

    @JsonProperty("end_time")
    private Instant endTime;

    @JsonProperty("status")
    private BookingStatus status;

    @JsonProperty("purpose")
    private String purpose;

    @JsonProperty("participants_count")
    private Integer participantsCount;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;
}
