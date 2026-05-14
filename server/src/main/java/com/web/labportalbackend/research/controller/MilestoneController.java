package com.web.labportalbackend.research.controller;

import com.web.labportalbackend.common.dto.Response;
import com.web.labportalbackend.research.dto.request.CreateMilestoneRequest;
import com.web.labportalbackend.research.dto.response.MilestoneResponse;
import com.web.labportalbackend.research.service.MilestoneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Research Milestone", description = "Project milestone management endpoints")
public class MilestoneController {

    private final MilestoneService milestoneService;

    @PostMapping("/milestones")
    @Operation(summary = "Create milestone")
    public ResponseEntity<Response<MilestoneResponse>> createMilestone(
            @Valid @RequestBody CreateMilestoneRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.ok("Milestone created successfully", milestoneService.createMilestone(request)));
    }

    @GetMapping("/projects/{id}/milestones")
    @Operation(summary = "Get milestones by project")
    public ResponseEntity<Response<List<MilestoneResponse>>> getByProject(@PathVariable Long id) {
        return ResponseEntity.ok(
                Response.ok("Milestones retrieved successfully", milestoneService.getByProject(id))
        );
    }
}
