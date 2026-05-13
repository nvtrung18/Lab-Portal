package com.web.labportalbackend.research.controller;

import com.web.labportalbackend.common.dto.Response;
import com.web.labportalbackend.research.dto.response.LogResponse;
import com.web.labportalbackend.research.service.LogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Research Audit Log", description = "Research project audit trail endpoints")
public class LogController {

    private final LogService logService;

    @GetMapping("/logs")
    @Operation(summary = "Get project audit logs", description = "Retrieve audit logs, optionally filtered by project_id")
    public ResponseEntity<Response<List<LogResponse>>> getLogs(
            @Parameter(description = "Optional research project ID filter", example = "1")
            @RequestParam(value = "project_id", required = false) Long projectId
    ) {
        return ResponseEntity.ok(
                Response.ok("Logs retrieved successfully", logService.getLogs(projectId))
        );
    }
}
