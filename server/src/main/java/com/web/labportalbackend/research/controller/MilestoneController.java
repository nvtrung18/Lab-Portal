package com.web.labportalbackend.research.controller;

import com.web.labportalbackend.common.dto.Response;
import com.web.labportalbackend.research.dto.request.CreateMilestoneRequest;
import com.web.labportalbackend.research.dto.request.UpdateMilestoneRequest;
import com.web.labportalbackend.research.dto.response.MilestoneResponse;
import com.web.labportalbackend.research.service.MilestoneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Research Milestone", description = "Project milestone management endpoints")
public class MilestoneController {

    private final MilestoneService milestoneService;

    @PostMapping("/milestones")
    @Operation(summary = "Create milestone")
    @PreAuthorize("hasRole('LAB_MANAGER')")
    public ResponseEntity<Response<MilestoneResponse>> createMilestone(
            @Valid @RequestBody CreateMilestoneRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.ok("Milestone created successfully", milestoneService.createMilestone(request)));
    }

    @GetMapping("/projects/{id}/milestones")
    @Operation(summary = "Get milestones by project")
    @PreAuthorize("hasAnyRole('LAB_MANAGER', 'STUDENT')")
    public ResponseEntity<Response<List<MilestoneResponse>>> getByProject(@PathVariable Long id) {
        return ResponseEntity.ok(
                Response.ok("Milestones retrieved successfully", milestoneService.getByProject(id))
        );
    }

    @GetMapping("/research-groups/{groupId}/milestones")
    @Operation(summary = "Get milestones by research group")
    @PreAuthorize("hasAnyRole('LAB_MANAGER', 'STUDENT')")
    public ResponseEntity<Response<List<MilestoneResponse>>> getByGroup(@PathVariable Long groupId) {
        return ResponseEntity.ok(
                Response.ok("Milestones retrieved successfully", milestoneService.getByGroup(groupId))
          );
    }

    @GetMapping("/research-groups/{groupId}/milestones/me")
    @Operation(summary = "Get current student milestones in research group")
    @PreAuthorize("hasAnyRole('LAB_MANAGER', 'STUDENT')")
    public ResponseEntity<Response<List<MilestoneResponse>>> getMyMilestonesInGroup(@PathVariable Long groupId) {
        return ResponseEntity.ok(
                Response.ok("My milestones retrieved successfully", milestoneService.getMyMilestonesInGroup(groupId))
        );
    }

    @PostMapping("/research-groups/{groupId}/milestones")
    @Operation(summary = "Create milestone in research group")
    @PreAuthorize("hasRole('LAB_MANAGER')")
    public ResponseEntity<Response<MilestoneResponse>> createMilestoneInGroup(
            @PathVariable Long groupId,
            @Valid @RequestBody CreateMilestoneRequest request
    ) {
        request.setGroupId(groupId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.ok("Milestone created successfully", milestoneService.createMilestone(request)));
    }

    @GetMapping("/milestones/{id}")
    @Operation(summary = "Get milestone detail")
    @PreAuthorize("hasAnyRole('LAB_MANAGER', 'STUDENT')")
    public ResponseEntity<Response<MilestoneResponse>> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(
                Response.ok("Milestone retrieved successfully", milestoneService.getDetail(id))
        );
    }

    @PutMapping("/milestones/{id}")
    @Operation(summary = "Update milestone")
    @PreAuthorize("hasRole('LAB_MANAGER')")
    public ResponseEntity<Response<MilestoneResponse>> updateMilestone(
            @PathVariable Long id,
            @Valid @RequestBody UpdateMilestoneRequest request
    ) {
        return ResponseEntity.ok(
                Response.ok("Milestone updated successfully", milestoneService.updateMilestone(id, request))
        );
    }
}
