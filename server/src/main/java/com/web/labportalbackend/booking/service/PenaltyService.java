package com.web.labportalbackend.booking.service;

import com.web.labportalbackend.booking.dto.request.PenaltyConfigRequest;
import com.web.labportalbackend.booking.dto.response.PenaltyConfigResponse;
import com.web.labportalbackend.booking.dto.response.PenaltyResponse;

import java.math.BigDecimal;
import java.util.List;

public interface PenaltyService {
    PenaltyConfigResponse updateConfig(PenaltyConfigRequest request);

    BigDecimal getCurrentPenaltyAmount();

    List<PenaltyResponse> getUserPenalties(Long userId);
}
