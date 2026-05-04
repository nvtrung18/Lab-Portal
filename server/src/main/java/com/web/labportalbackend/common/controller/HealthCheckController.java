package com.web.labportalbackend.common.controller;

import com.web.labportalbackend.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/system")
@Tag(name = "System", description = "System health and status endpoints")
public class HealthCheckController {

    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Check overall system health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> health() {
        Map<String, Object> health = Map.of(
                "status", "UP",
                "service", "Lab Portal Backend",
                "version", "1.0.0",
                "serverTime", Instant.now()
        );
        return ResponseEntity.ok(ApiResponse.success("System is running", health));
    }

    @GetMapping("/info")
    @Operation(summary = "System info", description = "Get system metadata")
    public ResponseEntity<ApiResponse<Map<String, String>>> info() {
        Map<String, String> info = Map.of(
                "name", "Lab Portal Backend",
                "description", "RESTful API for Lab Portal System",
                "version", "1.0.0",
                "architecture", "Modular Monolith"
        );
        return ResponseEntity.ok(ApiResponse.success("System information", info));
    }
}
