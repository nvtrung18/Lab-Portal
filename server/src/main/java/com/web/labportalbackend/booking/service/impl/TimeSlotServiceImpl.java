package com.web.labportalbackend.booking.service.impl;

import com.web.labportalbackend.booking.service.TimeSlotService;
import com.web.labportalbackend.booking.entity.TimeSlot;
import com.web.labportalbackend.booking.mapper.TimeSlotMapper;
import com.web.labportalbackend.booking.repository.TimeSlotRepository;
import com.web.labportalbackend.booking.dto.request.CreateTimeSlotRequest;
import com.web.labportalbackend.booking.dto.response.TimeSlotResponse;
import com.web.labportalbackend.common.enums.TimeSlotStatus;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TimeSlotServiceImpl implements TimeSlotService {
    private final TimeSlotRepository timeSlotRepository;
    private final LaboratoryRepository laboratoryRepository;

    @Override @Transactional
    public TimeSlotResponse createSlot(CreateTimeSlotRequest request) {
        Laboratory lab = laboratoryRepository.findById(request.getLabId()).orElseThrow(() -> new EntityNotFoundException("Lab not found: " + request.getLabId()));
        if (!request.getStartTime().isBefore(request.getEndTime())) throw new IllegalArgumentException("Start time must be before end time");
        if (request.getCapacity() <= 0) throw new IllegalArgumentException("Capacity must be positive");
        TimeSlot slot = TimeSlot.builder().lab(lab).startTime(request.getStartTime()).endTime(request.getEndTime()).capacity(request.getCapacity()).status(TimeSlotStatus.AVAILABLE).build();
        return TimeSlotMapper.toResponse(timeSlotRepository.save(slot));
    }

    @Override @Transactional(readOnly = true)
    public List<TimeSlotResponse> getSlotsByLab(Long labId) {
        if (!laboratoryRepository.existsById(labId)) throw new EntityNotFoundException("Lab not found: " + labId);
        return timeSlotRepository.findByLabId(labId).stream().map(TimeSlotMapper::toResponse).toList();
    }

    @Override @Transactional(readOnly = true)
    public TimeSlotResponse getSlotById(Long slotId) {
        return TimeSlotMapper.toResponse(timeSlotRepository.findActiveById(slotId).orElseThrow(() -> new EntityNotFoundException("Slot not found: " + slotId)));
    }

    @Override @Transactional
    public TimeSlotResponse updateSlotStatus(Long slotId, String status) {
        TimeSlot slot = timeSlotRepository.findActiveById(slotId).orElseThrow(() -> new EntityNotFoundException("Slot not found: " + slotId));
        slot.setStatus(TimeSlotStatus.valueOf(status.toUpperCase()));
        return TimeSlotMapper.toResponse(timeSlotRepository.save(slot));
    }

    @Override @Transactional
    public void deleteSlot(Long slotId) {
        TimeSlot slot = timeSlotRepository.findActiveById(slotId).orElseThrow(() -> new EntityNotFoundException("Slot not found: " + slotId));
        slot.setDeleted(true);
        timeSlotRepository.save(slot);
    }
}
