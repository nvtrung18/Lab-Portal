package com.web.labportalbackend.booking.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.web.labportalbackend.common.enums.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for check-in operation.
 * Contains confirmation details of the check-in transaction.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckinResponse {

    @JsonProperty("booking_id")
    private Long bookingId;

    @JsonProperty("user_id")
    private Long userId;

    @JsonProperty("lab_id")
    private Long labId;

    @JsonProperty("slot_id")
    private Long slotId;

    @JsonProperty("checked_in_at")
    private Instant checkedInAt;

    @JsonProperty("start_time")
    private Instant startTime;

    @JsonProperty("end_time")
    private Instant endTime;

    @JsonProperty("status")
    private BookingStatus status;

    @JsonProperty("message")
    private String message;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;
}
