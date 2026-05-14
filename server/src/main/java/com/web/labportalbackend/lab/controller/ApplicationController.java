package com.web.labportalbackend.lab.controller;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.lab.service.ApplicationService;
import com.web.labportalbackend.lab.dto.response.ApplicationResponseDTO;
import com.web.labportalbackend.lab.dto.request.ApplyRequestDTO;
import com.web.labportalbackend.common.dto.Response;
import com.web.labportalbackend.lab.dto.request.ReviewApplicationDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Application (CV submission) management endpoints.
 * Provides RESTful API for users to apply to laboratories and for admins to manage applications.
 */
@RestController
@RequestMapping("/applications")
@Tag(name = "Applications", description = "CV application management endpoints")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;
    private final UserRepository userRepository;

    /**
     * Submit a new CV application to a laboratory.
     *
     * @param labId the ID of the target laboratory
     * @param request the application request containing userId and cvUrl
     * @return the created application response
     */
    @PostMapping("/labs/{labId}/apply")
    @Operation(summary = "Apply to a laboratory", description = "Submit a CV application to a laboratory")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<Response<ApplicationResponseDTO>> apply(
            @PathVariable Long labId,
            @Valid @RequestBody ApplyRequestDTO request,
            Authentication authentication) {
        User currentUser = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new EntityNotFoundException("Current user not found"));
        ApplicationResponseDTO application = applicationService.apply(labId, currentUser.getId(), request.getCvUrl());
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(Response.ok("Application submitted successfully", application));
    }

    /**
     * Review an application (approve or reject).
     *
     * @param applicationId the ID of the application to review
     * @param request the review request containing the new status
     * @return the updated application response
     */
    @PutMapping("/{applicationId}/review")
    @Operation(summary = "Review an application", description = "Approve or reject a CV application")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<Response<ApplicationResponseDTO>> review(
            @PathVariable Long applicationId,
            @Valid @RequestBody ReviewApplicationDTO request) {
        ApplicationResponseDTO application = applicationService.review(applicationId, request.getStatus());
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(Response.ok("Application reviewed successfully", application));
    }

    /**
     * Retrieve all applications with pagination.
     *
     * @param pageable pagination parameters
     * @return paginated list of applications
     */
    @GetMapping
    @Operation(summary = "Get all applications", description = "Retrieve all applications with pagination")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<Response<Page<ApplicationResponseDTO>>> getApplications(
            @PageableDefault(size = 20, page = 0, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        Page<ApplicationResponseDTO> applications = applicationService.getApplications(pageable);
        return ResponseEntity.ok(Response.ok("Applications retrieved successfully", applications));
    }

    /**
     * Retrieve a specific application by ID.
     *
     * @param applicationId the ID of the application
     * @return the application details
     */
    @GetMapping("/{applicationId}")
    @Operation(summary = "Get application by ID", description = "Retrieve a specific application")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<Response<ApplicationResponseDTO>> getApplicationById(@PathVariable Long applicationId) {
        ApplicationResponseDTO application = applicationService.getApplicationById(applicationId);
        return ResponseEntity.ok(Response.ok("Application retrieved successfully", application));
    }

    /**
     * Retrieve all applications by a specific user.
     *
     * @param userId the ID of the user
     * @param pageable pagination parameters
     * @return paginated list of applications for the user
     */
    @GetMapping("/users/{userId}")
    @Operation(summary = "Get applications by user", description = "Retrieve all applications for a specific user")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<Response<Page<ApplicationResponseDTO>>> getApplicationsByUserId(
            @PathVariable Long userId,
            @PageableDefault(size = 20, page = 0, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        Page<ApplicationResponseDTO> applications = applicationService.getApplicationsByUserId(userId, pageable);
        return ResponseEntity.ok(Response.ok("Applications retrieved successfully", applications));
    }

    /**
     * Retrieve all applications for a specific laboratory.
     *
     * @param labId the ID of the laboratory
     * @param pageable pagination parameters
     * @return paginated list of applications for the laboratory
     */
    @GetMapping("/labs/{labId}")
    @Operation(summary = "Get applications by lab", description = "Retrieve all applications for a specific laboratory")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<Response<Page<ApplicationResponseDTO>>> getApplicationsByLabId(
            @PathVariable Long labId,
            @PageableDefault(size = 20, page = 0, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        Page<ApplicationResponseDTO> applications = applicationService.getApplicationsByLabId(labId, pageable);
        return ResponseEntity.ok(Response.ok("Applications retrieved successfully", applications));
    }
}
