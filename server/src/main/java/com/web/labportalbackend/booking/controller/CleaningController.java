package com.web.labportalbackend.booking.controller;

import com.web.labportalbackend.booking.dto.request.AssignCleaningRequest;
import com.web.labportalbackend.booking.dto.request.ConfirmCleaningRequest;
import com.web.labportalbackend.booking.dto.response.CleaningResponse;
import com.web.labportalbackend.booking.dto.response.EligibleCleanerResponse;
import com.web.labportalbackend.booking.service.CleaningService;
import com.web.labportalbackend.common.dto.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Cleaning", description = "Lab cleaning workflow endpoints")
public class CleaningController {

    private final CleaningService cleaningService;

    @GetMapping("/cleaning/pending")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Get pending cleaning tasks")
    public ResponseEntity<Response<List<CleaningResponse>>> getPendingCleanings() {
        return ResponseEntity.ok(
                Response.ok("Pending cleaning tasks retrieved successfully", cleaningService.getPendingCleanings())
        );
    }

    @PostMapping("/cleaning/assign")
    @PreAuthorize("hasRole('LAB_MANAGER')")
    @Operation(summary = "Assign staff to a cleaning task")
    public ResponseEntity<Response<CleaningResponse>> assignStaff(
            @Valid @RequestBody AssignCleaningRequest request
    ) {
        return ResponseEntity.ok(
                Response.ok("Cleaning task assigned successfully",
                        cleaningService.assignStaff(request.getCleaningId(), request.getStaffId()))
        );
    }

    @GetMapping("/labs/{labId}/cleaning-tasks")
    @PreAuthorize("hasRole('LAB_MANAGER')")
    @Operation(summary = "Get lab cleaning tasks")
    public ResponseEntity<Response<List<CleaningResponse>>> getLabCleaningTasks(@PathVariable Long labId) {
        return ResponseEntity.ok(
                Response.ok("Cleaning tasks retrieved successfully", cleaningService.getLabCleaningTasks(labId))
        );
    }

    @GetMapping("/slots/{slotId}/eligible-cleaners")
    @PreAuthorize("hasRole('LAB_MANAGER')")
    @Operation(summary = "Get eligible cleaners for slot")
    public ResponseEntity<Response<List<EligibleCleanerResponse>>> getEligibleCleaners(@PathVariable Long slotId) {
        return ResponseEntity.ok(
                Response.ok("Eligible cleaners retrieved successfully", cleaningService.getEligibleCleaners(slotId))
        );
    }

    @PostMapping("/cleaning-tasks")
    @PreAuthorize("hasRole('LAB_MANAGER')")
    @Operation(summary = "Assign cleaning tasks")
    public ResponseEntity<Response<List<CleaningResponse>>> assignCleaningTasks(
            @Valid @RequestBody AssignCleaningRequest request
    ) {
        return ResponseEntity.ok(
                Response.ok("Cleaning tasks assigned successfully",
                        cleaningService.assignCleaningTasks(request.getSlotId(), request.getAssigneeIds()))
        );
    }

    @GetMapping("/users/me/cleaning-tasks")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Get current user's cleaning tasks")
    public ResponseEntity<Response<List<CleaningResponse>>> getMyCleaningTasks() {
        return ResponseEntity.ok(
                Response.ok("Cleaning tasks retrieved successfully", cleaningService.getMyCleaningTasks())
        );
    }

    @PatchMapping("/cleaning-tasks/{id}/complete")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Complete cleaning task")
    public ResponseEntity<Response<CleaningResponse>> completeCleaningTask(@PathVariable Long id) {
        return ResponseEntity.ok(
                Response.ok("Cleaning task completed successfully", cleaningService.completeCleaningTask(id))
        );
    }

    @PatchMapping("/cleaning-tasks/{id}/cancel")
    @PreAuthorize("hasRole('LAB_MANAGER')")
    @Operation(summary = "Cancel cleaning task")
    public ResponseEntity<Response<CleaningResponse>> cancelCleaningTask(@PathVariable Long id) {
        return ResponseEntity.ok(
                Response.ok("Cleaning task cancelled successfully", cleaningService.cancelCleaningTask(id))
        );
    }

    @PostMapping("/cleaning/confirm")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Confirm a cleaning task as completed")
    public ResponseEntity<Response<CleaningResponse>> confirmCompleted(
            @Valid @RequestBody ConfirmCleaningRequest request
    ) {
        return ResponseEntity.ok(
                Response.ok("Cleaning task completed successfully",
                        cleaningService.confirmCompleted(request.getCleaningId()))
        );
    }
}
