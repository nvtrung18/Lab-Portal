package com.web.labportalbackend.booking.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class PenaltyConfigResponse {
    private BigDecimal amount;
}
