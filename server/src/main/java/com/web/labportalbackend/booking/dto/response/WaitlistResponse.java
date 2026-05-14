package com.web.labportalbackend.booking.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for waitlist data.
 * <p>
 * Returned when a user is added to a time slot's waitlist.
 * Contains position info and timestamps for frontend display.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaitlistResponse {
    @JsonProperty("id") private Long id;
    @JsonProperty("slot_id") private Long slotId;
    @JsonProperty("user_id") private Long userId;
    @JsonProperty("position") private Integer position;
    @JsonProperty("created_at") private Instant createdAt;
    @JsonProperty("updated_at") private Instant updatedAt;
}
