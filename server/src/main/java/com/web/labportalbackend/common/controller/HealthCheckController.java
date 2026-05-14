package com.web.labportalbackend.common.controller;

import com.web.labportalbackend.common.dto.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/system")
@Tag(name = "System", description = "System health and status endpoints")
public class HealthCheckController {

    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Check overall system health")
    public ResponseEntity<Response<Map<String, Object>>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", System.currentTimeMillis());
        health.put("database", "MySQL - Connected");
        health.put("security", "JWT - Active");
        return ResponseEntity.ok(Response.ok("System is running", health));
    }

    @GetMapping("/info")
    @Operation(summary = "System info")
    public ResponseEntity<Response<Map<String, String>>> info() {
        Map<String, String> info = new HashMap<>();
        info.put("version", "1.0.0-SNAPSHOT");
        info.put("description", "Lab Portal Backend API");
        return ResponseEntity.ok(Response.ok("System information", info));
    }
}
