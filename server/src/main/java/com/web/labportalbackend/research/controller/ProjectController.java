package com.web.labportalbackend.research.controller;

import com.web.labportalbackend.common.dto.Response;
import com.web.labportalbackend.research.dto.request.CreateProjectRequest;
import com.web.labportalbackend.research.dto.request.CreateResearchProjectRequest;
import com.web.labportalbackend.research.dto.response.ProjectDetailResponse;
import com.web.labportalbackend.research.dto.response.ProjectResponse;
import com.web.labportalbackend.research.service.ProjectService;
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
@Tag(name = "Research Project", description = "Research project management endpoints")
public class ProjectController {

    private final ProjectService projectService;

    @GetMapping("/labs/{labId}/research-projects")
    @Operation(summary = "Get research projects by lab")
    @PreAuthorize("hasAnyRole('LAB_MANAGER', 'STUDENT')")
    public ResponseEntity<Response<List<ProjectResponse>>> getByLab(@PathVariable Long labId) {
        return ResponseEntity.ok(
                Response.ok("Research projects retrieved successfully", projectService.getByLab(labId))
        );
    }

    @PostMapping("/research-projects")
    @Operation(summary = "Create research project")
    @PreAuthorize("hasRole('LAB_MANAGER')")
    public ResponseEntity<Response<ProjectResponse>> createResearchProject(
            @Valid @RequestBody CreateResearchProjectRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.ok("Research project created successfully", projectService.createResearchProject(request)));
    }

    @PutMapping("/research-projects/{id}")
    @Operation(summary = "Update research project")
    @PreAuthorize("hasRole('LAB_MANAGER')")
    public ResponseEntity<Response<ProjectResponse>> updateResearchProject(
            @PathVariable Long id,
            @Valid @RequestBody CreateResearchProjectRequest request
    ) {
        return ResponseEntity.ok(
                Response.ok("Research project updated successfully", projectService.updateResearchProject(id, request))
        );
    }

    @PostMapping("/projects")
    @Operation(summary = "Create project")
    @PreAuthorize("hasRole('LAB_MANAGER')")
    public ResponseEntity<Response<ProjectResponse>> createProject(
            @Valid @RequestBody CreateProjectRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.ok("Project created successfully", projectService.createProject(request)));
    }

    @GetMapping("/groups/{id}/projects")
    @Operation(summary = "Get projects by group")
    @PreAuthorize("hasAnyRole('LAB_MANAGER', 'STUDENT')")
    public ResponseEntity<Response<List<ProjectResponse>>> getByGroup(@PathVariable Long id) {
        return ResponseEntity.ok(
                Response.ok("Projects retrieved successfully", projectService.getByGroup(id))
        );
    }

    @GetMapping("/projects/{id}")
    @Operation(summary = "Get project detail")
    @PreAuthorize("hasAnyRole('LAB_MANAGER', 'STUDENT')")
    public ResponseEntity<Response<ProjectDetailResponse>> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(
                Response.ok("Project detail retrieved successfully", projectService.getDetail(id))
        );
    }

    @GetMapping("/research-projects/{id}")
    @Operation(summary = "Get research project detail")
    @PreAuthorize("hasAnyRole('LAB_MANAGER', 'STUDENT')")
    public ResponseEntity<Response<ProjectDetailResponse>> getResearchProjectDetail(@PathVariable Long id) {
        return ResponseEntity.ok(
                Response.ok("Research project detail retrieved successfully", projectService.getDetail(id))
        );
    }
}
