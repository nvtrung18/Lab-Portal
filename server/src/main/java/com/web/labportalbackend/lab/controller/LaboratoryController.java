package com.web.labportalbackend.lab.controller;

import com.web.labportalbackend.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Laboratory management endpoints skeleton.
 * Service layer will be connected in Day 3.
 */
@RestController
@RequestMapping("/labs")
@Tag(name = "Laboratory", description = "Laboratory management endpoints")
public class LaboratoryController {

    @GetMapping("/health")
    @Operation(summary = "Lab service health check")
    public ResponseEntity<ApiResponse<String>> health() {
        return ResponseEntity.ok(ApiResponse.success("Lab service is healthy", "UP"));
    }

    @GetMapping
    @Operation(summary = "Get all laboratories", description = "Retrieve all available laboratories with pagination")
    public ResponseEntity<ApiResponse<String>> getAllLabs() {
        return ResponseEntity.ok(ApiResponse.success("Get all laboratories — service pending"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get laboratory by ID")
    public ResponseEntity<ApiResponse<String>> getLabById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Get laboratory by ID — service pending", "ID: " + id));
    }

    @PostMapping
    @Operation(summary = "Create laboratory", description = "Create a new laboratory")
    public ResponseEntity<ApiResponse<String>> createLab() {
        return ResponseEntity.ok(ApiResponse.success("Create laboratory — service pending"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update laboratory", description = "Update an existing laboratory")
    public ResponseEntity<ApiResponse<String>> updateLab(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Update laboratory — service pending", "ID: " + id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete laboratory", description = "Soft-delete a laboratory")
    public ResponseEntity<ApiResponse<String>> deleteLab(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Delete laboratory — service pending", "ID: " + id));
    }
}
