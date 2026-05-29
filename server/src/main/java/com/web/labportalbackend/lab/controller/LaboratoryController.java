package com.web.labportalbackend.lab.controller;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.dto.Response;
import com.web.labportalbackend.lab.dto.request.ApplyRequestDTO;
import com.web.labportalbackend.lab.dto.request.CreateLabRequest;
import com.web.labportalbackend.lab.dto.request.UpdateLabStatusRequest;
import com.web.labportalbackend.lab.dto.response.ApplicationResponseDTO;
import com.web.labportalbackend.lab.dto.response.LabMemberResponse;
import com.web.labportalbackend.lab.dto.response.LabResponse;
import com.web.labportalbackend.lab.dto.response.LabDashboardStatsResponse;
import com.web.labportalbackend.lab.entity.Membership;

import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import com.web.labportalbackend.lab.repository.MembershipRepository;
import com.web.labportalbackend.lab.service.ApplicationService;
import com.web.labportalbackend.lab.service.LabService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
    private final MembershipRepository membershipRepository;
    private final LaboratoryRepository laboratoryRepository;

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

    @GetMapping("/{id}/dashboard/stats")
    @Operation(summary = "Get laboratory dashboard statistics")
    @SecurityRequirement(name = "Bearer Authentication")
    @PreAuthorize("hasRole('LAB_MANAGER')")
    public ResponseEntity<Response<LabDashboardStatsResponse>> getLabDashboardStats(
            @PathVariable Long id,
            Authentication authentication) {
        // Assert the user can manage/view this lab
        assertCanViewLabMembers(id, authentication);
        LabDashboardStatsResponse stats = labService.getLabDashboardStats(id);
        return ResponseEntity.ok(Response.ok("Laboratory dashboard statistics retrieved successfully", stats));
    }

    @GetMapping("/{id}/members")
    @Operation(summary = "Get laboratory members", description = "Retrieve ACTIVE members of a laboratory")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<Response<List<LabMemberResponse>>> getLabMembers(
            @PathVariable Long id,
            Authentication authentication) {
        assertCanViewLabMembers(id, authentication);
        List<LabMemberResponse> members = membershipRepository.findByLaboratoryIdAndDeletedFalse(id).stream()
                .filter(membership -> Boolean.TRUE.equals(membership.getActive()))
                .map(this::toLabMemberResponse)
                .toList();
        return ResponseEntity.ok(Response.ok("Laboratory members retrieved successfully", members));
    }

    @GetMapping("/{id}/research-eligible-students")
    @Operation(summary = "Get research eligible students", description = "Retrieve ACTIVE student members of a managed laboratory")
    @SecurityRequirement(name = "Bearer Authentication")
    @PreAuthorize("hasRole('LAB_MANAGER')")
    public ResponseEntity<Response<List<LabMemberResponse>>> getResearchEligibleStudents(
            @PathVariable Long id,
            Authentication authentication) {
        assertCanViewLabMembers(id, authentication);
        List<LabMemberResponse> students = membershipRepository.findByLaboratoryIdAndDeletedFalse(id).stream()
                .filter(membership -> Boolean.TRUE.equals(membership.getActive()))
                .filter(membership -> membership.getUser().hasRole("STUDENT"))
                .map(this::toLabMemberResponse)
                .toList();
        return ResponseEntity.ok(Response.ok("Research eligible students retrieved successfully", students));
    }

    @PatchMapping("/{id}/members/{userId}/remove")
    @Operation(summary = "Remove laboratory member", description = "Remove a member from the managed laboratory without deleting the user account")
    @SecurityRequirement(name = "Bearer Authentication")
    @PreAuthorize("hasRole('LAB_MANAGER')")
    public ResponseEntity<Response<LabMemberResponse>> removeLabMember(
            @PathVariable Long id,
            @PathVariable Long userId,
            Authentication authentication) {
        User currentUser = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new EntityNotFoundException("Current user not found"));
        Laboratory managedLab = getManagedLabForManager(currentUser);

        if (!managedLab.getId().equals(id)) {
            throw new AccessDeniedException("Cannot remove members from another lab");
        }
        if (currentUser.getId().equals(userId)) {
            throw new AccessDeniedException("Lab manager cannot remove themselves from the lab");
        }

        Membership membership = membershipRepository.findByUserIdAndLaboratoryIdAndDeletedFalse(userId, id)
                .orElseThrow(() -> new EntityNotFoundException("Membership not found"));
        if (!Boolean.TRUE.equals(membership.getActive())) {
            throw new IllegalArgumentException("Membership is not active");
        }

        membership.setActive(false);
        Membership updated = membershipRepository.save(membership);
        return ResponseEntity.ok(Response.ok("Member removed from laboratory successfully", toLabMemberResponse(updated)));
    }

    @PostMapping(value = "/{id}/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Apply to a laboratory", description = "Submit a CV application to a laboratory")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<Response<ApplicationResponseDTO>> apply(
            @PathVariable Long id,
            @RequestParam(value = "cvUrl", required = false) String cvUrl,
            @RequestPart(value = "cvFile", required = false) MultipartFile cvFile,
            Authentication authentication) {
        User currentUser = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new EntityNotFoundException("Current user not found"));
        ApplicationResponseDTO application = applicationService.apply(id, currentUser.getId(), cvUrl, cvFile);
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

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update laboratory status", description = "Deactivate or restore a laboratory (ADMIN only)")
    @SecurityRequirement(name = "Bearer Authentication")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Response<LabResponse>> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateLabStatusRequest request) {
        LabResponse laboratory = labService.updateStatus(id, request.getStatus());
        return ResponseEntity.ok(Response.ok("Laboratory status updated successfully", laboratory));
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

    private void assertCanViewLabMembers(Long labId, Authentication authentication) {
        User currentUser = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new EntityNotFoundException("Current user not found"));

        if (currentUser.hasRole("ADMIN")) {
            return;
        }

        if (!currentUser.hasRole("LAB_MANAGER")) {
            throw new AccessDeniedException("Only lab managers can view lab members");
        }

        Laboratory managedLab = getManagedLabForManager(currentUser);
        if (!managedLab.getId().equals(labId)) {
            throw new AccessDeniedException("Cannot view members from another lab");
        }
    }

    private Laboratory getManagedLabForManager(User currentUser) {
        if (!currentUser.hasRole("LAB_MANAGER")) {
            throw new AccessDeniedException("Only lab managers can manage lab members");
        }

        return laboratoryRepository.findFirstByManagerIdAndDeletedFalse(currentUser.getId())
                .orElseThrow(() -> new AccessDeniedException("Lab manager is not assigned to any lab"));
    }

    private LabMemberResponse toLabMemberResponse(Membership membership) {
        return LabMemberResponse.builder()
                .id(membership.getId())
                .userId(membership.getUser().getId())
                .fullName(membership.getUser().getFullName())
                .email(membership.getUser().getEmail())
                .labId(membership.getLaboratory().getId())
                .labName(membership.getLaboratory().getLabName())
                .role(membership.getRole())
                .status(Boolean.TRUE.equals(membership.getActive()) ? "ACTIVE" : "INACTIVE")
                .joinedAt(membership.getCreatedAt())
                .build();
    }
}
