package com.web.labportalbackend.booking;

import com.web.labportalbackend.booking.entity.TimeSlot;
import com.web.labportalbackend.booking.mapper.TimeSlotMapper;
import com.web.labportalbackend.booking.repository.TimeSlotRepository;
import com.web.labportalbackend.common.dto.CreateTimeSlotRequest;
import com.web.labportalbackend.common.dto.TimeSlotResponse;
import com.web.labportalbackend.common.enums.TimeSlotStatus;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementation of TimeSlotService.
 * Handles business logic for time slot creation, retrieval, and management.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TimeSlotServiceImpl implements TimeSlotService {

    private final TimeSlotRepository timeSlotRepository;
    private final LaboratoryRepository laboratoryRepository;

    /**
     * Create a new time slot with comprehensive validation.
     * Validates:
     * - Lab exists
     * - start_time is before end_time
     * - Capacity is positive
     */
    @Override
    @Transactional
    public TimeSlotResponse createSlot(CreateTimeSlotRequest request) {
        log.debug("Creating time slot for lab: {}", request.getLabId());

        // Validation: Lab must exist
        Laboratory lab = laboratoryRepository.findById(request.getLabId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Laboratory not found with ID: " + request.getLabId()
                ));

        // Validation: start_time must be before end_time
        if (!request.getStartTime().isBefore(request.getEndTime())) {
            log.warn("Invalid time range: start_time must be before end_time. Start: {}, End: {}",
                    request.getStartTime(), request.getEndTime());
            throw new IllegalArgumentException("Start time must be before end time");
        }

        // Validation: Capacity must be positive
        if (request.getCapacity() <= 0) {
            log.warn("Invalid capacity: {}", request.getCapacity());
            throw new IllegalArgumentException("Capacity must be greater than 0");
        }

        // Create and save time slot
        TimeSlot timeSlot = TimeSlot.builder()
                .lab(lab)
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .capacity(request.getCapacity())
                .status(TimeSlotStatus.AVAILABLE)
                .build();

        TimeSlot saved = timeSlotRepository.save(timeSlot);
        log.info("Time slot created successfully with ID: {}", saved.getId());

        return TimeSlotMapper.toResponse(saved);
    }

    /**
     * Retrieve all active time slots for a specific laboratory.
     */
    @Override
    @Transactional(readOnly = true)
    public List<TimeSlotResponse> getSlotsByLab(Long labId) {
        log.debug("Fetching time slots for lab: {}", labId);

        // Validation: Lab must exist
        if (!laboratoryRepository.existsById(labId)) {
            throw new EntityNotFoundException("Laboratory not found with ID: " + labId);
        }

        List<TimeSlot> slots = timeSlotRepository.findByLabId(labId);
        log.debug("Found {} time slots for lab: {}", slots.size(), labId);

        return slots.stream()
                .map(TimeSlotMapper::toResponse)
                .toList();
    }

    /**
     * Retrieve a single time slot by ID.
     */
    @Override
    @Transactional(readOnly = true)
    public TimeSlotResponse getSlotById(Long slotId) {
        log.debug("Fetching time slot with ID: {}", slotId);

        TimeSlot timeSlot = timeSlotRepository.findActiveById(slotId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Time slot not found with ID: " + slotId
                ));

        return TimeSlotMapper.toResponse(timeSlot);
    }

    /**
     * Update the status of a time slot.
     */
    @Override
    @Transactional
    public TimeSlotResponse updateSlotStatus(Long slotId, String status) {
        log.debug("Updating time slot {} status to: {}", slotId, status);

        TimeSlot timeSlot = timeSlotRepository.findActiveById(slotId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Time slot not found with ID: " + slotId
                ));

        try {
            TimeSlotStatus newStatus = TimeSlotStatus.valueOf(status.toUpperCase());
            timeSlot.setStatus(newStatus);
            TimeSlot updated = timeSlotRepository.save(timeSlot);
            log.info("Time slot {} status updated to {}", slotId, newStatus);
            return TimeSlotMapper.toResponse(updated);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid status provided: {}", status);
            throw new IllegalArgumentException(
                    "Invalid status: " + status + ". Must be one of: AVAILABLE, FULL, CANCELLED"
            );
        }
    }

    /**
     * Soft-delete a time slot by marking it as deleted.
     */
    @Override
    @Transactional
    public void deleteSlot(Long slotId) {
        log.debug("Deleting time slot with ID: {}", slotId);

        TimeSlot timeSlot = timeSlotRepository.findActiveById(slotId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Time slot not found with ID: " + slotId
                ));

        timeSlot.setDeleted(true);
        timeSlotRepository.save(timeSlot);
        log.info("Time slot {} marked as deleted", slotId);
    }

}
