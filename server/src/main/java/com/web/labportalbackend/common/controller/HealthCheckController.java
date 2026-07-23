package com.web.labportalbackend.common.controller;

import com.web.labportalbackend.common.dto.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/system")
@Tag(name = "System", description = "System health and status endpoints")
public class HealthCheckController {

    @GetMapping("/health")
    @Operation(summary = "Legacy health check", description = "Deprecated compatibility endpoint; use /actuator/health")
    @Deprecated
    public ResponseEntity<Response<Map<String, Object>>> health() {
        Map<String, Object> health = Map.of(
                "status", "UP",
                "timestamp", System.currentTimeMillis(),
                "actuator", "/api/actuator/health"
        );
        return ResponseEntity.ok(Response.ok("System is running", health));
    }

    @GetMapping("/info")
    @Operation(summary = "System info")
    public ResponseEntity<Response<Map<String, String>>> info() {
        Map<String, String> info = Map.of(
                "version", "1.0.0-SNAPSHOT",
                "description", "Lab Portal Backend API"
        );
        return ResponseEntity.ok(Response.ok("System information", info));
    }
}
