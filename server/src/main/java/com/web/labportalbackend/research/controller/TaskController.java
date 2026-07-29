package com.web.labportalbackend.research.controller;

import com.web.labportalbackend.common.dto.Response;
import com.web.labportalbackend.research.dto.request.CreateResearchTaskRequest;
import com.web.labportalbackend.research.dto.request.CreateTaskRequest;
import com.web.labportalbackend.research.dto.request.PatchResearchTaskRequest;
import com.web.labportalbackend.research.dto.request.PatchTaskStatusRequest;
import com.web.labportalbackend.research.dto.request.UpdateTaskStatusRequest;
import com.web.labportalbackend.research.dto.response.ProjectTaskBoardResponse;
import com.web.labportalbackend.research.dto.response.TaskBacklogPageResponse;
import com.web.labportalbackend.research.dto.response.TaskResponse;
import com.web.labportalbackend.research.enums.TaskPriority;
import com.web.labportalbackend.research.enums.TaskStatus;
import com.web.labportalbackend.research.enums.TaskType;
import com.web.labportalbackend.research.service.TaskBoardReadService;
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
    private final TaskBoardReadService taskBoardReadService;

    @PostMapping("/research/tasks")
    @Operation(summary = "Create an official research task")
    @PreAuthorize("hasRole('LAB_MANAGER')")
    public ResponseEntity<Response<TaskResponse>> createResearchTask(
            @Valid @RequestBody CreateResearchTaskRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.ok("Task created successfully", taskService.createResearchTask(request)));
    }

    @PatchMapping("/research/tasks/{taskId}")
    @Operation(summary = "Patch research task metadata")
    @PreAuthorize("hasAnyRole('LAB_MANAGER','STUDENT')")
    public ResponseEntity<Response<TaskResponse>> patchResearchTask(
            @PathVariable Long taskId,
            @Valid @RequestBody PatchResearchTaskRequest request
    ) {
        return ResponseEntity.ok(
                Response.ok("Task metadata updated successfully", taskService.patchResearchTask(taskId, request))
        );
    }

    @PatchMapping("/research/tasks/{taskId}/status")
    @Operation(summary = "Update canonical research task status")
    @PreAuthorize("hasAnyRole('LAB_MANAGER','STUDENT')")
    public ResponseEntity<Response<TaskResponse>> patchResearchTaskStatus(
            @PathVariable Long taskId,
            @Valid @RequestBody PatchTaskStatusRequest request
    ) {
        return ResponseEntity.ok(
                Response.ok("Task status updated successfully",
                        taskService.patchResearchTaskStatus(taskId, request))
        );
    }

    @GetMapping("/research/projects/{projectId}/board")
    @Operation(summary = "Get project task board")
    @PreAuthorize("hasAnyRole('LAB_MANAGER','STUDENT')")
    public ResponseEntity<Response<ProjectTaskBoardResponse>> getProjectBoard(
            @PathVariable Long projectId,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false) TaskType type,
            @RequestParam(defaultValue = "false") boolean includeBacklog,
            @RequestParam(defaultValue = "false") boolean includeCancelled
    ) {
        return ResponseEntity.ok(Response.ok(
                "Project task board retrieved successfully",
                taskBoardReadService.read(projectId, groupId, assigneeId, status, priority, type,
                        includeBacklog, includeCancelled)
        ));
    }

    @GetMapping("/research/projects/{projectId}/backlog")
    @Operation(summary = "Get project task backlog")
    @PreAuthorize("hasAnyRole('LAB_MANAGER','STUDENT')")
    public ResponseEntity<Response<TaskBacklogPageResponse>> getProjectBacklog(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(Response.ok(
                "Project backlog retrieved successfully",
                taskBoardReadService.readBacklog(projectId, page, size)
        ));
    }

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
