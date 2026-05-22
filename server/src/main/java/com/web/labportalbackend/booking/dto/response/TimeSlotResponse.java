package com.web.labportalbackend.booking.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.web.labportalbackend.common.enums.TimeSlotStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for time slot data.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeSlotResponse {
    @JsonProperty("id") private Long id;
    @JsonProperty("labId") private Long labId;
    @JsonProperty("startTime") private Instant startTime;
    @JsonProperty("endTime") private Instant endTime;
    @JsonProperty("capacity") private Integer capacity;
    @JsonProperty("bookedCount") private Long bookedCount;
    @JsonProperty("status") private TimeSlotStatus status;
    @JsonProperty("createdAt") private Instant createdAt;
    @JsonProperty("updatedAt") private Instant updatedAt;
}
