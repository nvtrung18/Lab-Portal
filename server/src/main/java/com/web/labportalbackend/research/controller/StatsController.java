package com.web.labportalbackend.research.controller;

import com.web.labportalbackend.common.dto.Response;
import com.web.labportalbackend.research.dto.response.ProjectStatsOverviewResponse;
import com.web.labportalbackend.research.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "Research Stats", description = "Research project statistics endpoints")
public class StatsController {

    private final StatsService statsService;

    @GetMapping(value = "/projects/{projectId}/stats", params = "type=overview")
    @PreAuthorize("hasAnyRole('LAB_MANAGER', 'STUDENT')")
    @Operation(summary = "Get project stats overview")
    public ResponseEntity<Response<ProjectStatsOverviewResponse>> getProjectStats(
            @PathVariable Long projectId,
            @Parameter(description = "Stats view type. Currently only overview is supported.")
            @RequestParam(defaultValue = "overview") String type
    ) {
        return ResponseEntity.ok(
                Response.ok("Project stats retrieved successfully", statsService.getProjectStats(projectId))
        );
    }
}
