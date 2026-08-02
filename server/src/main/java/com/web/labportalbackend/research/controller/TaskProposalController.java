package com.web.labportalbackend.research.controller;

import com.web.labportalbackend.common.dto.Response;
import com.web.labportalbackend.research.dto.request.CreateTaskProposalRequest;
import com.web.labportalbackend.research.dto.request.RejectTaskProposalRequest;
import com.web.labportalbackend.research.dto.response.TaskProposalResponse;
import com.web.labportalbackend.research.dto.response.TaskProposalReviewResponse;
import com.web.labportalbackend.research.dto.response.TaskProposalPageResponse;
import com.web.labportalbackend.research.enums.TaskProposalStatus;
import com.web.labportalbackend.research.service.TaskProposalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequiredArgsConstructor
public class TaskProposalController {

    private final TaskProposalService taskProposalService;

    @GetMapping("/research/task-proposals")
    @PreAuthorize("hasAnyRole('LAB_MANAGER','STUDENT')")
    public ResponseEntity<Response<TaskProposalPageResponse>> list(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) TaskProposalStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(Response.ok("Task proposals retrieved successfully",
                taskProposalService.list(projectId, groupId, status, page, size)));
    }

    @PostMapping("/research/task-proposals")
    @PreAuthorize("""
            hasRole('STUDENT')
            and authentication.authorities.?[
                authority.startsWith('ROLE_')
            ].size() == 1
            """)
    public ResponseEntity<Response<TaskProposalResponse>> submit(
            @Valid @RequestBody CreateTaskProposalRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.ok(
                        "Task proposal submitted successfully",
                        taskProposalService.submit(request)
                ));
    }

    @PostMapping("/research/task-proposals/{proposalId}/approve")
    @PreAuthorize("hasAnyRole('LAB_MANAGER','STUDENT')")
    public ResponseEntity<Response<TaskProposalReviewResponse>> approve(
            @PathVariable Long proposalId
    ) {
        return ResponseEntity.ok(Response.ok(
                "Task proposal approved successfully",
                taskProposalService.approve(proposalId)
        ));
    }

    @PostMapping("/research/task-proposals/{proposalId}/reject")
    @PreAuthorize("hasAnyRole('LAB_MANAGER','STUDENT')")
    public ResponseEntity<Response<TaskProposalReviewResponse>> reject(
            @PathVariable Long proposalId,
            @Valid @RequestBody RejectTaskProposalRequest request
    ) {
        return ResponseEntity.ok(Response.ok(
                "Task proposal rejected successfully",
                taskProposalService.reject(proposalId, request)
        ));
    }
}
