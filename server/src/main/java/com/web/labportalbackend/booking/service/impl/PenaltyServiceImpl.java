package com.web.labportalbackend.booking.service.impl;

import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.booking.dto.request.PenaltyConfigRequest;
import com.web.labportalbackend.booking.dto.response.PenaltyConfigResponse;
import com.web.labportalbackend.booking.dto.response.PenaltyResponse;
import com.web.labportalbackend.booking.mapper.PenaltyMapper;
import com.web.labportalbackend.booking.repository.PenaltyRepository;
import com.web.labportalbackend.booking.service.PenaltyService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
public class PenaltyServiceImpl implements PenaltyService {

    private final PenaltyRepository penaltyRepository;
    private final UserRepository userRepository;
    private final AtomicReference<BigDecimal> configuredAmount = new AtomicReference<>();

    @Value("${booking.penalty.default-amount:0}")
    private BigDecimal defaultAmount;

    @Override
    public PenaltyConfigResponse updateConfig(PenaltyConfigRequest request) {
        configuredAmount.set(request.getAmount());
        return PenaltyConfigResponse.builder()
                .amount(request.getAmount())
                .build();
    }

    @Override
    public BigDecimal getCurrentPenaltyAmount() {
        BigDecimal amount = configuredAmount.get();
        return amount != null ? amount : defaultAmount;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PenaltyResponse> getUserPenalties(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new EntityNotFoundException("User not found: " + userId);
        }
        return penaltyRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(PenaltyMapper::toResponse)
                .toList();
    }
}
