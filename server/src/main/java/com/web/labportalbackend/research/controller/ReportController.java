package com.web.labportalbackend.research.controller;

import com.web.labportalbackend.common.dto.Response;
import com.web.labportalbackend.research.dto.request.SubmitReportRequest;
import com.web.labportalbackend.research.dto.response.ReportResponse;
import com.web.labportalbackend.research.service.ReportService;
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
@Tag(name = "Research Report", description = "Versioned task report submission endpoints")
public class ReportController {

    private final ReportService reportService;

    @PostMapping("/reports")
    @Operation(summary = "Submit task report")
    public ResponseEntity<Response<ReportResponse>> submitReport(
            @Valid @RequestBody SubmitReportRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.ok("Report submitted successfully", reportService.submitReport(request)));
    }

    @GetMapping("/tasks/{id}/reports")
    @Operation(summary = "Get report versions by task")
    public ResponseEntity<Response<List<ReportResponse>>> getReportsByTask(@PathVariable Long id) {
        return ResponseEntity.ok(
                Response.ok("Reports retrieved successfully", reportService.getReportsByTask(id))
        );
    }
}
