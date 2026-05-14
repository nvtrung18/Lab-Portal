package com.web.labportalbackend.booking.controller;

import com.web.labportalbackend.booking.service.TimeSlotService;
import com.web.labportalbackend.booking.dto.request.CreateTimeSlotRequest;
import com.web.labportalbackend.common.dto.Response;
import com.web.labportalbackend.booking.dto.response.TimeSlotResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for time slot management.
 * Handles CRUD operations for lab time slots used in booking system.
 */
@Slf4j
@RestController
@RequestMapping("/api/slots")
@RequiredArgsConstructor
@Tag(name = "TimeSlot", description = "Lab time slot management endpoints")
public class TimeSlotController {

    private final TimeSlotService timeSlotService;

    /**
     * Create a new time slot.
     *
     * @param request the creation request containing lab ID, time range, and capacity
     * @return the created time slot wrapped in Response
     */
    @PostMapping
    @Operation(summary = "Create time slot", description = "Create a new lab time slot with capacity")
    public ResponseEntity<Response<TimeSlotResponse>> createSlot(
            @Valid @RequestBody CreateTimeSlotRequest request
    ) {
        log.info("Creating time slot for lab: {}", request.getLabId());
        TimeSlotResponse slot = timeSlotService.createSlot(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(Response.ok("Time slot created successfully", slot));
    }

    /**
     * Get all active time slots for a specific lab.
     *
     * @param labId the lab ID
     * @return list of time slots wrapped in Response
     */
    @GetMapping("/labs/{labId}")
    @Operation(summary = "Get time slots by lab", description = "Retrieve all active time slots for a specific lab")
    public ResponseEntity<Response<List<TimeSlotResponse>>> getSlotsByLab(
            @PathVariable Long labId
    ) {
        log.info("Fetching time slots for lab: {}", labId);
        List<TimeSlotResponse> slots = timeSlotService.getSlotsByLab(labId);
        return ResponseEntity.ok(
                Response.ok("Time slots retrieved successfully", slots)
        );
    }

    /**
     * Get a specific time slot by ID.
     *
     * @param slotId the time slot ID
     * @return the time slot wrapped in Response
     */
    @GetMapping("/{slotId}")
    @Operation(summary = "Get time slot by ID", description = "Retrieve a specific time slot")
    public ResponseEntity<Response<TimeSlotResponse>> getSlotById(
            @PathVariable Long slotId
    ) {
        log.info("Fetching time slot: {}", slotId);
        TimeSlotResponse slot = timeSlotService.getSlotById(slotId);
        return ResponseEntity.ok(
                Response.ok("Time slot retrieved successfully", slot)
        );
    }

    /**
     * Update the status of a time slot.
     *
     * @param slotId the time slot ID
     * @param status the new status (AVAILABLE, FULL, CANCELLED)
     * @return the updated time slot wrapped in Response
     */
    @PatchMapping("/{slotId}/status")
    @Operation(summary = "Update time slot status", description = "Update time slot status to AVAILABLE, FULL, or CANCELLED")
    public ResponseEntity<Response<TimeSlotResponse>> updateSlotStatus(
            @PathVariable Long slotId,
            @RequestParam String status
    ) {
        log.info("Updating time slot {} status to: {}", slotId, status);
        TimeSlotResponse slot = timeSlotService.updateSlotStatus(slotId, status);
        return ResponseEntity.ok(
                Response.ok("Time slot status updated successfully", slot)
        );
    }

    /**
     * Delete (soft-delete) a time slot.
     *
     * @param slotId the time slot ID
     * @return success message wrapped in Response
     */
    @DeleteMapping("/{slotId}")
    @Operation(summary = "Delete time slot", description = "Soft-delete a time slot")
    public ResponseEntity<Response<Void>> deleteSlot(
            @PathVariable Long slotId
    ) {
        log.info("Deleting time slot: {}", slotId);
        timeSlotService.deleteSlot(slotId);
        return ResponseEntity.ok(
                Response.ok("Time slot deleted successfully")
        );
    }
}
