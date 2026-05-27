package com.web.labportalbackend.booking.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckinQrResponse {
    @JsonProperty("token")
    private String token;

    @JsonProperty("expiresAt")
    private Instant expiresAt;

    @JsonProperty("message")
    private String message;
}
