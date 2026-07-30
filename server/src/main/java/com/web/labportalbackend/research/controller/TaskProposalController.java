package com.web.labportalbackend.research.controller;

import com.web.labportalbackend.common.dto.Response;
import com.web.labportalbackend.research.dto.request.CreateTaskProposalRequest;
import com.web.labportalbackend.research.dto.response.TaskProposalResponse;
import com.web.labportalbackend.research.service.TaskProposalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TaskProposalController {

    private final TaskProposalService taskProposalService;

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
}
