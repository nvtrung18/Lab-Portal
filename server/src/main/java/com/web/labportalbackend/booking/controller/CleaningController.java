package com.web.labportalbackend.booking.controller;

import com.web.labportalbackend.booking.dto.request.AssignCleaningRequest;
import com.web.labportalbackend.booking.dto.request.ConfirmCleaningRequest;
import com.web.labportalbackend.booking.dto.response.CleaningResponse;
import com.web.labportalbackend.booking.service.CleaningService;
import com.web.labportalbackend.common.dto.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/cleaning")
@RequiredArgsConstructor
@Tag(name = "Cleaning", description = "Lab cleaning workflow endpoints")
public class CleaningController {

    private final CleaningService cleaningService;

    @GetMapping("/pending")
    @Operation(summary = "Get pending cleaning tasks")
    public ResponseEntity<Response<List<CleaningResponse>>> getPendingCleanings() {
        return ResponseEntity.ok(
                Response.ok("Pending cleaning tasks retrieved successfully", cleaningService.getPendingCleanings())
        );
    }

    @PostMapping("/assign")
    @Operation(summary = "Assign staff to a cleaning task")
    public ResponseEntity<Response<CleaningResponse>> assignStaff(
            @Valid @RequestBody AssignCleaningRequest request
    ) {
        return ResponseEntity.ok(
                Response.ok("Cleaning task assigned successfully",
                        cleaningService.assignStaff(request.getCleaningId(), request.getStaffId()))
        );
    }

    @PostMapping("/confirm")
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
