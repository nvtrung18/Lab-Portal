package com.web.labportalbackend.booking.service.impl;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.booking.dto.response.CleaningResponse;
import com.web.labportalbackend.booking.entity.CleaningEntity;
import com.web.labportalbackend.booking.entity.TimeSlot;
import com.web.labportalbackend.booking.mapper.CleaningMapper;
import com.web.labportalbackend.booking.repository.CleaningRepository;
import com.web.labportalbackend.booking.repository.TimeSlotRepository;
import com.web.labportalbackend.booking.service.CleaningService;
import com.web.labportalbackend.common.enums.CleaningStatus;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CleaningServiceImpl implements CleaningService {

    private final CleaningRepository cleaningRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public CleaningResponse createCleaningTask(Long slotId) {
        return CleaningMapper.toResponse(createCleaningTaskEntity(slotId));
    }

    @Transactional
    public CleaningEntity createCleaningTaskEntity(Long slotId) {
        return cleaningRepository.findBySlotId(slotId).orElseGet(() -> {
            TimeSlot slot = timeSlotRepository.findById(slotId)
                    .orElseThrow(() -> new EntityNotFoundException("Time slot not found: " + slotId));
            CleaningEntity cleaning = CleaningEntity.builder()
                    .slot(slot)
                    .status(CleaningStatus.PENDING)
                    .build();
            return cleaningRepository.save(cleaning);
        });
    }

    @Override
    @Transactional
    public CleaningResponse assignStaff(Long cleaningId, Long staffId) {
        CleaningEntity cleaning = cleaningRepository.findById(cleaningId)
                .orElseThrow(() -> new EntityNotFoundException("Cleaning task not found: " + cleaningId));
        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new EntityNotFoundException("Staff not found: " + staffId));

        if (cleaning.getStatus() == CleaningStatus.COMPLETED) {
            throw new IllegalStateException("Completed cleaning task cannot be reassigned");
        }

        cleaning.setStaff(staff);
        cleaning.setStatus(CleaningStatus.ASSIGNED);
        cleaning.setStartedAt(Instant.now());
        return CleaningMapper.toResponse(cleaningRepository.save(cleaning));
    }

    @Override
    @Transactional
    public CleaningResponse confirmCompleted(Long cleaningId) {
        CleaningEntity cleaning = cleaningRepository.findById(cleaningId)
                .orElseThrow(() -> new EntityNotFoundException("Cleaning task not found: " + cleaningId));

        if (cleaning.getStatus() == CleaningStatus.PENDING) {
            throw new IllegalStateException("Cleaning task must be assigned before completion");
        }
        if (cleaning.getStatus() == CleaningStatus.COMPLETED) {
            return CleaningMapper.toResponse(cleaning);
        }

        cleaning.setStatus(CleaningStatus.COMPLETED);
        cleaning.setCompletedAt(Instant.now());
        return CleaningMapper.toResponse(cleaningRepository.save(cleaning));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CleaningResponse> getPendingCleanings() {
        return cleaningRepository.findByStatus(CleaningStatus.PENDING)
                .stream()
                .map(CleaningMapper::toResponse)
                .toList();
    }
}
