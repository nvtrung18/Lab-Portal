package com.web.labportalbackend.research.controller;

import com.web.labportalbackend.common.dto.Response;
import com.web.labportalbackend.research.dto.request.CreateResearchLogRequest;
import com.web.labportalbackend.research.dto.response.ResearchLogResponse;
import com.web.labportalbackend.research.enums.ResearchLogType;
import com.web.labportalbackend.research.service.ResearchLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Research Log", description = "Research progress timeline endpoints")
public class ResearchLogController {

    private final ResearchLogService researchLogService;

    @GetMapping("/projects/{projectId}/logs")
    @Operation(summary = "Get research logs by project")
    @PreAuthorize("hasAnyRole('LAB_MANAGER', 'STUDENT')")
    public ResponseEntity<Response<List<ResearchLogResponse>>> getProjectLogs(
            @PathVariable Long projectId,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) Long milestoneId,
            @RequestParam(required = false) Long taskId,
            @RequestParam(required = false) Long authorId,
            @RequestParam(required = false) ResearchLogType logType,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return ResponseEntity.ok(Response.ok(
                "Research logs retrieved successfully",
                researchLogService.getProjectLogs(projectId, groupId, milestoneId, taskId, authorId, logType, page, size)
        ));
    }

    @GetMapping("/research-groups/{groupId}/logs")
    @Operation(summary = "Get research logs by group")
    @PreAuthorize("hasAnyRole('LAB_MANAGER', 'STUDENT')")
    public ResponseEntity<Response<List<ResearchLogResponse>>> getGroupLogs(
            @PathVariable Long groupId,
            @RequestParam(required = false) Long milestoneId,
            @RequestParam(required = false) Long taskId,
            @RequestParam(required = false) Long authorId,
            @RequestParam(required = false) ResearchLogType logType,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return ResponseEntity.ok(Response.ok(
                "Research logs retrieved successfully",
                researchLogService.getGroupLogs(groupId, milestoneId, taskId, authorId, logType, page, size)
        ));
    }

    @PostMapping("/logs")
    @Operation(summary = "Create a manual research log")
    @PreAuthorize("hasAnyRole('LAB_MANAGER', 'STUDENT')")
    public ResponseEntity<Response<ResearchLogResponse>> createLog(
            @Valid @RequestBody CreateResearchLogRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.ok("Research log created successfully", researchLogService.createManualLog(request)));
    }
}
