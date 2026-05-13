package com.web.labportalbackend.research.controller;

import com.web.labportalbackend.common.dto.Response;
import com.web.labportalbackend.research.dto.request.AssignTaskRequest;
import com.web.labportalbackend.research.dto.request.CreateTaskRequest;
import com.web.labportalbackend.research.dto.response.TaskResponse;
import com.web.labportalbackend.research.service.TaskService;
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
@Tag(name = "Research Task", description = "Milestone task create, assign, and listing endpoints")
public class TaskController {

    private final TaskService taskService;

    @PostMapping("/tasks")
    @Operation(summary = "Create task")
    public ResponseEntity<Response<TaskResponse>> createTask(
            @Valid @RequestBody CreateTaskRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.ok("Task created successfully", taskService.createTask(request)));
    }

    @PutMapping("/tasks/{id}/assign")
    @Operation(summary = "Assign task")
    public ResponseEntity<Response<TaskResponse>> assign(
            @PathVariable Long id,
            @Valid @RequestBody AssignTaskRequest request
    ) {
        return ResponseEntity.ok(
                Response.ok("Task assigned successfully", taskService.assign(id, request))
        );
    }

    @GetMapping("/milestones/{id}/tasks")
    @Operation(summary = "Get tasks by milestone")
    public ResponseEntity<Response<List<TaskResponse>>> getByMilestone(@PathVariable Long id) {
        return ResponseEntity.ok(
                Response.ok("Tasks retrieved successfully", taskService.getByMilestone(id))
        );
    }
}
