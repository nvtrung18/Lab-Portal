package com.web.labportalbackend.research.controller;

import com.web.labportalbackend.common.dto.Response;
import com.web.labportalbackend.research.dto.request.CreateProjectRequest;
import com.web.labportalbackend.research.dto.response.ProjectDetailResponse;
import com.web.labportalbackend.research.dto.response.ProjectResponse;
import com.web.labportalbackend.research.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Research Project", description = "Research project management endpoints")
public class ProjectController {

    private final ProjectService projectService;

    @PostMapping("/projects")
    @Operation(summary = "Create project")
    public ResponseEntity<Response<ProjectResponse>> createProject(
            @Valid @RequestBody CreateProjectRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.ok("Project created successfully", projectService.createProject(request)));
    }

    @GetMapping("/groups/{id}/projects")
    @Operation(summary = "Get projects by group")
    public ResponseEntity<Response<List<ProjectResponse>>> getByGroup(@PathVariable Long id) {
        return ResponseEntity.ok(
                Response.ok("Projects retrieved successfully", projectService.getByGroup(id))
        );
    }

    @GetMapping("/projects/{id}")
    @Operation(summary = "Get project detail")
    public ResponseEntity<Response<ProjectDetailResponse>> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(
                Response.ok("Project detail retrieved successfully", projectService.getDetail(id))
        );
    }
}
