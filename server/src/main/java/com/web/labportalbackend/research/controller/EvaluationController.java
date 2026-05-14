package com.web.labportalbackend.research.controller;

import com.web.labportalbackend.common.dto.Response;
import com.web.labportalbackend.research.dto.request.EvaluationRequest;
import com.web.labportalbackend.research.dto.response.EvaluationResponse;
import com.web.labportalbackend.research.service.EvaluationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Research Evaluation", description = "Project evaluation endpoints")
public class EvaluationController {

    private final EvaluationService evaluationService;

    @PostMapping("/evaluations")
    @Operation(summary = "Evaluate project", description = "Create a score-based evaluation for a research project")
    public ResponseEntity<Response<EvaluationResponse>> evaluateProject(
            @Valid @RequestBody EvaluationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.ok("Project evaluated successfully", evaluationService.evaluateProject(request.getProjectId(), request)));
    }

    @GetMapping("/projects/{id}/evaluations")
    @Operation(summary = "Get project evaluations", description = "Retrieve evaluations for a project")
    public ResponseEntity<Response<List<EvaluationResponse>>> getEvaluationsByProject(@PathVariable Long id) {
        return ResponseEntity.ok(
                Response.ok("Evaluations retrieved successfully", evaluationService.getByProject(id))
        );
    }
}
