package com.web.labportalbackend.research.controller;

import com.web.labportalbackend.common.dto.Response;
import com.web.labportalbackend.research.dto.request.CreateTaskRequest;
import com.web.labportalbackend.research.dto.request.UpdateTaskStatusRequest;
import com.web.labportalbackend.research.dto.response.TaskResponse;
import com.web.labportalbackend.research.service.TaskService;
import org.springframework.http.HttpStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Research Task", description = "Research task board endpoints")
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

    @GetMapping({"/groups/{id}/tasks", "/research-groups/{id}/tasks"})
    @Operation(summary = "Get visible task board by research group")
    @PreAuthorize("hasAnyRole('LAB_MANAGER', 'STUDENT')")
    public ResponseEntity<Response<List<TaskResponse>>> getByGroup(@PathVariable Long id) {
        return ResponseEntity.ok(
                Response.ok("Tasks retrieved successfully", taskService.getByGroup(id))
        );
    }

    @GetMapping("/research-groups/{id}/tasks/me")
    @Operation(summary = "Get the current student's tasks in research group")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Response<List<TaskResponse>>> getMyTasksInGroup(@PathVariable Long id) {
        return ResponseEntity.ok(
                Response.ok("My tasks retrieved successfully", taskService.getMyTasksInGroup(id))
        );
    }

    @PostMapping("/milestones/{milestoneId}/tasks")
    @Operation(summary = "Create task in milestone")
    @PreAuthorize("hasRole('LAB_MANAGER')")
    public ResponseEntity<Response<TaskResponse>> createTaskInMilestone(
            @PathVariable Long milestoneId,
            @Valid @RequestBody CreateTaskRequest request
    ) {
        request.setMilestoneId(milestoneId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.ok("Task created successfully", taskService.createTask(request)));
    }

    @PutMapping("/tasks/{id}/status")
    @Operation(summary = "Update task status from task board")
    @PreAuthorize("hasAnyRole('LAB_MANAGER', 'STUDENT')")
    public ResponseEntity<Response<TaskResponse>> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTaskStatusRequest request
    ) {
        return ResponseEntity.ok(
                Response.ok("Đã cập nhật trạng thái nhiệm vụ.", taskService.updateStatus(id, request))
        );
    }
}
