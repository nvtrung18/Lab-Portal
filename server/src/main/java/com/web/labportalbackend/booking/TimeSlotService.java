package com.web.labportalbackend.booking.service;

import com.web.labportalbackend.common.dto.CreateTimeSlotRequest;
import com.web.labportalbackend.common.dto.TimeSlotResponse;

import java.util.List;

/**
 * Service interface for time slot management.
 * Defines business logic contracts for slot creation, retrieval, and manipulation.
 */
public interface TimeSlotService {

    /**
     * Create a new time slot with validation.
     *
     * @param request the time slot creation request containing lab ID, time range, and capacity
     * @return the created time slot response
     * @throws IllegalArgumentException if start_time >= end_time or other validation fails
     * @throws jakarta.persistence.EntityNotFoundException if lab_id doesn't reference an existing lab
     */
    TimeSlotResponse createSlot(CreateTimeSlotRequest request);

    /**
     * Retrieve all active time slots for a specific laboratory.
     *
     * @param labId the lab ID
     * @return list of time slot responses for the lab
     * @throws jakarta.persistence.EntityNotFoundException if lab_id doesn't reference an existing lab
     */
    List<TimeSlotResponse> getSlotsByLab(Long labId);

    /**
     * Retrieve a single time slot by ID.
     *
     * @param slotId the time slot ID
     * @return the time slot response
     * @throws jakarta.persistence.EntityNotFoundException if slot not found or inactive
     */
    TimeSlotResponse getSlotById(Long slotId);

    /**
     * Update the status of a time slot.
     *
     * @param slotId the time slot ID
     * @param status the new status (as string: AVAILABLE, FULL, CANCELLED)
     * @return the updated time slot response
     * @throws jakarta.persistence.EntityNotFoundException if slot not found
     * @throws IllegalArgumentException if status is invalid
     */
    TimeSlotResponse updateSlotStatus(Long slotId, String status);

    /**
     * Soft-delete a time slot by marking it as deleted.
     *
     * @param slotId the time slot ID
     * @throws jakarta.persistence.EntityNotFoundException if slot not found
     */
    void deleteSlot(Long slotId);
}
