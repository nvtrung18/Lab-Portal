package com.web.labportalbackend.booking.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.web.labportalbackend.common.enums.BookingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for booking data.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingResponse {
    @JsonProperty("id") private Long id;
    @JsonProperty("userId") private Long userId;
    @JsonProperty("studentName") private String studentName;
    @JsonProperty("studentEmail") private String studentEmail;
    @JsonProperty("labId") private Long labId;
    @JsonProperty("labName") private String labName;
    @JsonProperty("slotId") private Long slotId;
    @JsonProperty("startTime") private Instant startTime;
    @JsonProperty("endTime") private Instant endTime;
    @JsonProperty("status") private BookingStatus status;
    @JsonProperty("purpose") private String purpose;
    @JsonProperty("participantsCount") private Integer participantsCount;
    @JsonProperty("createdAt") private Instant createdAt;
    @JsonProperty("updatedAt") private Instant updatedAt;
}
