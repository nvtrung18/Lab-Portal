package com.web.labportalbackend.research.controller;

import com.web.labportalbackend.common.dto.Response;
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
    public ResponseEntity<Response<String>> health() {
        return ResponseEntity.ok(Response.ok("Research service is healthy", "UP"));
    }

    @GetMapping
    @Operation(summary = "Get all research projects", description = "Retrieve all research projects with pagination")
    public ResponseEntity<Response<String>> getAllProjects() {
        return ResponseEntity.ok(Response.ok("Get all research projects — service pending"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get project by ID")
    public ResponseEntity<Response<String>> getProjectById(@PathVariable Long id) {
        return ResponseEntity.ok(Response.ok("Get research project — service pending", "ID: " + id));
    }

    @PostMapping
    @Operation(summary = "Create research project")
    public ResponseEntity<Response<String>> createProject() {
        return ResponseEntity.ok(Response.ok("Create research project — service pending"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update research project")
    public ResponseEntity<Response<String>> updateProject(@PathVariable Long id) {
        return ResponseEntity.ok(Response.ok("Update research project — service pending", "ID: " + id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete research project")
    public ResponseEntity<Response<String>> deleteProject(@PathVariable Long id) {
        return ResponseEntity.ok(Response.ok("Delete research project — service pending", "ID: " + id));
    }

    @GetMapping("/labs/{labId}")
    @Operation(summary = "Get projects by laboratory")
    public ResponseEntity<Response<String>> getProjectsByLab(@PathVariable Long labId) {
        return ResponseEntity.ok(Response.ok("Get projects by lab — service pending", "Lab ID: " + labId));
    }

    @GetMapping("/leaders/{leaderId}")
    @Operation(summary = "Get projects by leader")
    public ResponseEntity<Response<String>> getProjectsByLeader(@PathVariable Long leaderId) {
        return ResponseEntity.ok(Response.ok("Get projects by leader — service pending", "Leader ID: " + leaderId));
    }
}
