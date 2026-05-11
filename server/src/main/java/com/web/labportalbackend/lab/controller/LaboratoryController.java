package com.web.labportalbackend.lab.controller;

import com.web.labportalbackend.lab.service.LabService;
import com.web.labportalbackend.lab.dto.request.CreateLabRequest;
import com.web.labportalbackend.lab.dto.response.LabResponse;
import com.web.labportalbackend.common.dto.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Laboratory management endpoints.
 * Provides RESTful API for lab operations including creation and manager assignment.
 */
@RestController
@RequestMapping("/api/labs")
@Tag(name = "Laboratory", description = "Laboratory management endpoints")
@RequiredArgsConstructor
public class LaboratoryController {

    private final LabService labService;

    @GetMapping("/health")
    @Operation(summary = "Lab service health check")
    public ResponseEntity<Response<String>> health() {
        return ResponseEntity.ok(Response.ok("Lab service is healthy", "UP"));
    }

    @PostMapping
    @Operation(summary = "Create laboratory", description = "Create a new laboratory (ADMIN only)")
    @SecurityRequirement(name = "Bearer Authentication")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Response<LabResponse>> createLab(@Valid @RequestBody CreateLabRequest request) {
        LabResponse laboratory = labService.createLab(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(Response.ok("Laboratory created successfully", laboratory));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get laboratory by ID")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<Response<LabResponse>> getLabById(@PathVariable Long id) {
        LabResponse laboratory = labService.getLabById(id);
        return ResponseEntity.ok(Response.ok("Laboratory retrieved successfully", laboratory));
    }

    @PutMapping("/{id}/manager")
    @Operation(summary = "Assign manager to laboratory", description = "Assign a lab manager (ADMIN only)")
    @SecurityRequirement(name = "Bearer Authentication")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Response<LabResponse>> assignManager(
            @PathVariable Long id,
            @RequestParam Long managerId) {
        LabResponse laboratory = labService.assignManager(id, managerId);
        return ResponseEntity.ok(Response.ok("Manager assigned successfully", laboratory));
    }

    @GetMapping
    @Operation(summary = "Get all laboratories", description = "Retrieve all available laboratories")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<Response<String>> getAllLabs() {
        return ResponseEntity.ok(Response.ok("Get all laboratories — service pending"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update laboratory", description = "Update an existing laboratory")
    @SecurityRequirement(name = "Bearer Authentication")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Response<String>> updateLab(@PathVariable Long id) {
        return ResponseEntity.ok(Response.ok("Update laboratory — service pending", "ID: " + id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete laboratory", description = "Soft-delete a laboratory")
    @SecurityRequirement(name = "Bearer Authentication")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Response<String>> deleteLab(@PathVariable Long id) {
        return ResponseEntity.ok(Response.ok("Delete laboratory — service pending", "ID: " + id));
    }
}
