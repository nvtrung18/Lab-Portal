package com.web.labportalbackend.lab.controller;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.dto.Response;
import com.web.labportalbackend.lab.dto.request.ApplyRequestDTO;
import com.web.labportalbackend.lab.dto.request.CreateLabRequest;
import com.web.labportalbackend.lab.dto.response.ApplicationResponseDTO;
import com.web.labportalbackend.lab.dto.response.LabResponse;
import com.web.labportalbackend.lab.service.ApplicationService;
import com.web.labportalbackend.lab.service.LabService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Laboratory management endpoints.
 */
@RestController
@RequestMapping("/labs")
@Tag(name = "Laboratory", description = "Laboratory management endpoints")
@RequiredArgsConstructor
public class LaboratoryController {

    private final LabService labService;
    private final ApplicationService applicationService;
    private final UserRepository userRepository;

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

    @GetMapping
    @Operation(summary = "Get all laboratories", description = "Retrieve all available laboratories")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<Response<List<LabResponse>>> getAllLabs() {
        return ResponseEntity.ok(Response.ok("Laboratories retrieved successfully", labService.getAllLabs()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get laboratory by ID")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<Response<LabResponse>> getLabById(@PathVariable Long id) {
        LabResponse laboratory = labService.getLabById(id);
        return ResponseEntity.ok(Response.ok("Laboratory retrieved successfully", laboratory));
    }

    @PostMapping("/{id}/apply")
    @Operation(summary = "Apply to a laboratory", description = "Submit a CV application to a laboratory")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<Response<ApplicationResponseDTO>> apply(
            @PathVariable Long id,
            @Valid @RequestBody ApplyRequestDTO request,
            Authentication authentication) {
        User currentUser = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new EntityNotFoundException("Current user not found"));
        ApplicationResponseDTO application = applicationService.apply(id, currentUser.getId(), request.getCvUrl());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(Response.ok("Application submitted successfully", application));
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

    @PutMapping("/{id}")
    @Operation(summary = "Update laboratory", description = "Update an existing laboratory")
    @SecurityRequirement(name = "Bearer Authentication")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Response<String>> updateLab(@PathVariable Long id) {
        return ResponseEntity.ok(Response.ok("Update laboratory - service pending", "ID: " + id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete laboratory", description = "Soft-delete a laboratory")
    @SecurityRequirement(name = "Bearer Authentication")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Response<String>> deleteLab(@PathVariable Long id) {
        return ResponseEntity.ok(Response.ok("Delete laboratory - service pending", "ID: " + id));
    }
}
