package com.web.labportalbackend.research.controller;

import com.web.labportalbackend.common.dto.Response;
import com.web.labportalbackend.research.dto.response.TaskResponse;
import com.web.labportalbackend.research.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Research Task", description = "Read-only milestone task board endpoint")
public class TaskController {

    private final TaskService taskService;

    @GetMapping("/milestones/{id}/tasks")
    @Operation(summary = "Get read-only task board by milestone")
    @PreAuthorize("hasAnyRole('LAB_MANAGER', 'STUDENT')")
    public ResponseEntity<Response<List<TaskResponse>>> getByMilestone(@PathVariable Long id) {
        return ResponseEntity.ok(
                Response.ok("Tasks retrieved successfully", taskService.getByMilestone(id))
        );
    }
}
