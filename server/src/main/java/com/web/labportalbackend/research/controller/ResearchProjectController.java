package com.web.labportalbackend.research.controller;

import com.web.labportalbackend.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Research project management endpoints skeleton.
 * Service layer will be connected in Day 4.
 */
@RestController
@RequestMapping("/research-projects")
@Tag(name = "Research", description = "Research project management endpoints")
public class ResearchProjectController {

    @GetMapping("/health")
    @Operation(summary = "Research service health check")
    public ResponseEntity<ApiResponse<String>> health() {
        return ResponseEntity.ok(ApiResponse.success("Research service is healthy", "UP"));
    }

    @GetMapping
    @Operation(summary = "Get all research projects")
    public ResponseEntity<ApiResponse<String>> getAllProjects() {
        return ResponseEntity.ok(ApiResponse.success("Get all research projects — service pending"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get research project by ID")
    public ResponseEntity<ApiResponse<String>> getProjectById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Get research project — service pending", "ID: " + id));
    }

    @PostMapping
    @Operation(summary = "Create research project")
    public ResponseEntity<ApiResponse<String>> createProject() {
        return ResponseEntity.ok(ApiResponse.success("Create research project — service pending"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update research project")
    public ResponseEntity<ApiResponse<String>> updateProject(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Update research project — service pending", "ID: " + id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete research project", description = "Soft-delete a research project")
    public ResponseEntity<ApiResponse<String>> deleteProject(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Delete research project — service pending", "ID: " + id));
    }

    @GetMapping("/lab/{labId}")
    @Operation(summary = "Get projects by lab")
    public ResponseEntity<ApiResponse<String>> getProjectsByLab(@PathVariable Long labId) {
        return ResponseEntity.ok(ApiResponse.success("Get projects by lab — service pending", "Lab ID: " + labId));
    }

    @GetMapping("/leader/{leaderId}")
    @Operation(summary = "Get projects by leader")
    public ResponseEntity<ApiResponse<String>> getProjectsByLeader(@PathVariable Long leaderId) {
        return ResponseEntity.ok(ApiResponse.success("Get projects by leader — service pending", "Leader ID: " + leaderId));
    }
}
