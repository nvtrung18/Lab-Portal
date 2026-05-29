package com.web.labportalbackend.research.controller;

import com.web.labportalbackend.common.dto.Response;
import com.web.labportalbackend.research.dto.request.SubmitReportRequest;
import com.web.labportalbackend.research.dto.request.LeaderReviewReportRequest;
import com.web.labportalbackend.research.dto.request.ManagerReviewReportRequest;
import com.web.labportalbackend.research.dto.response.ReportResponse;
import com.web.labportalbackend.research.dto.response.ReportFileDownload;
import com.web.labportalbackend.research.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.nio.charset.StandardCharsets;

@RestController
@RequiredArgsConstructor
@Tag(name = "Research Report", description = "Versioned task report submission endpoints")
public class ReportController {

    private final ReportService reportService;

    @PostMapping(value = "/reports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Submit an assigned task report with a required attachment")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Response<ReportResponse>> submitReport(
            @Valid @ModelAttribute SubmitReportRequest request,
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.ok("Đã nộp báo cáo.", reportService.submitReport(request, file)));
    }

    @GetMapping("/milestones/{id}/reports")
    @Operation(summary = "Get report submission history by milestone")
    @PreAuthorize("hasAnyRole('LAB_MANAGER', 'STUDENT')")
    public ResponseEntity<Response<List<ReportResponse>>> getReportsByMilestone(@PathVariable Long id) {
        return ResponseEntity.ok(
                Response.ok("Reports retrieved successfully", reportService.getReportsByMilestone(id))
        );
    }

    @GetMapping("/milestones/{id}/reports/me")
    @Operation(summary = "Get the current student's report submission history by milestone")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Response<List<ReportResponse>>> getMyReportsByMilestone(@PathVariable Long id) {
        return ResponseEntity.ok(
                Response.ok("My reports retrieved successfully", reportService.getMyReportsByMilestone(id))
        );
    }

    @GetMapping("/tasks/{id}/reports")
    @Operation(summary = "Get report versions by task")
    @PreAuthorize("hasAnyRole('LAB_MANAGER', 'STUDENT')")
    public ResponseEntity<Response<List<ReportResponse>>> getReportsByTask(@PathVariable Long id) {
        return ResponseEntity.ok(
                Response.ok("Reports retrieved successfully", reportService.getReportsByTask(id))
        );
    }

    @GetMapping("/reports/{id}/file")
    @Operation(summary = "Download an authorized task report attachment")
    @PreAuthorize("hasAnyRole('LAB_MANAGER', 'STUDENT')")
    public ResponseEntity<Resource> downloadReportFile(@PathVariable Long id) {
        ReportFileDownload file = reportService.downloadReportFile(id);
        String contentDisposition = ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build()
                .toString();
        MediaType fileType = file.fileType() == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(file.fileType());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .contentType(fileType)
                .body(file.resource());
    }

    @GetMapping({"/groups/{id}/reports", "/research-groups/{id}/reports"})
    @Operation(summary = "Get member report submissions by group")
    @PreAuthorize("hasAnyRole('LAB_MANAGER', 'STUDENT')")
    public ResponseEntity<Response<List<ReportResponse>>> getReportsByGroup(@PathVariable Long id) {
        return ResponseEntity.ok(
                Response.ok("Group reports retrieved successfully", reportService.getReportsByGroup(id))
        );
    }

    @GetMapping({"/groups/{id}/reports/me", "/research-groups/{id}/reports/me"})
    @Operation(summary = "Get the current student's report submissions by group")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Response<List<ReportResponse>>> getMyReportsByGroup(@PathVariable Long id) {
        return ResponseEntity.ok(
                Response.ok("My group reports retrieved successfully", reportService.getMyReportsByGroup(id))
        );
    }

    @GetMapping("/labs/{id}/reports/pending-review")
    @Operation(summary = "Get reports awaiting final review in a managed laboratory")
    @PreAuthorize("hasRole('LAB_MANAGER')")
    public ResponseEntity<Response<List<ReportResponse>>> getPendingManagerReviewByLab(@PathVariable Long id) {
        return ResponseEntity.ok(
                Response.ok("Pending reports retrieved successfully", reportService.getPendingManagerReviewByLab(id))
        );
    }

    @PatchMapping("/reports/{id}/leader-review")
    @Operation(summary = "Mark report as reviewed by group leader")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Response<ReportResponse>> leaderReview(
            @PathVariable Long id,
            @Valid @RequestBody LeaderReviewReportRequest request
    ) {
        return ResponseEntity.ok(
                Response.ok("Report reviewed by group leader", reportService.leaderReview(id, request))
        );
    }

    @PatchMapping("/reports/{id}/manager-review")
    @Operation(summary = "Make final manager decision for a reviewed report")
    @PreAuthorize("hasRole('LAB_MANAGER')")
    public ResponseEntity<Response<ReportResponse>> managerReview(
            @PathVariable Long id,
            @Valid @RequestBody ManagerReviewReportRequest request
    ) {
        return ResponseEntity.ok(
                Response.ok("Manager review completed", reportService.managerReview(id, request))
        );
    }
}
