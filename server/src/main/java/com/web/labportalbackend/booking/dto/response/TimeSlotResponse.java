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
    @JsonProperty("lab_id") private Long labId;
    @JsonProperty("start_time") private Instant startTime;
    @JsonProperty("end_time") private Instant endTime;
    @JsonProperty("capacity") private Integer capacity;
    @JsonProperty("status") private TimeSlotStatus status;
    @JsonProperty("created_at") private Instant createdAt;
    @JsonProperty("updated_at") private Instant updatedAt;
}
