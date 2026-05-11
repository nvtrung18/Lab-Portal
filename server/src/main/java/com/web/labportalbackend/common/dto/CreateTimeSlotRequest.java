package com.web.labportalbackend.common.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Request DTO for creating a new time slot.
 * Includes validation annotations to ensure data integrity.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTimeSlotRequest {

    @NotNull(message = "Lab ID is required")
    @JsonProperty("lab_id")
    private Long labId;

    @NotNull(message = "Start time is required")
    @JsonProperty("start_time")
    private Instant startTime;

    @NotNull(message = "End time is required")
    @JsonProperty("end_time")
    private Instant endTime;

    @NotNull(message = "Capacity is required")
    @Min(value = 1, message = "Capacity must be at least 1")
    @JsonProperty("capacity")
    private Integer capacity;
}
