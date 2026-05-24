package com.web.labportalbackend.research.controller;

import com.web.labportalbackend.common.dto.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Legacy research project endpoint skeleton kept only for health checks.
 * Functional manager project endpoints live in {@link ProjectController}.
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
}
