package com.web.labportalbackend.booking.controller;

import com.web.labportalbackend.booking.dto.request.ComplaintRequest;
import com.web.labportalbackend.booking.dto.request.ReviewComplaintRequest;
import com.web.labportalbackend.booking.dto.response.ComplaintResponse;
import com.web.labportalbackend.booking.service.ComplaintService;
import com.web.labportalbackend.common.dto.Response;
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
@Tag(name = "Complaint", description = "User complaint endpoints")
public class ComplaintController {

    private final ComplaintService complaintService;

    @PostMapping("/complaints")
    @Operation(summary = "Submit complaint")
    public ResponseEntity<Response<ComplaintResponse>> submitComplaint(
            @Valid @RequestBody ComplaintRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.ok("Complaint submitted successfully", complaintService.submitComplaint(request)));
    }

    @GetMapping("/labs/{labId}/complaints")
    @Operation(summary = "Get lab complaints")
    public ResponseEntity<Response<List<ComplaintResponse>>> getLabComplaints(@PathVariable Long labId) {
        return ResponseEntity.ok(
                Response.ok("Complaints retrieved successfully", complaintService.getLabComplaints(labId))
        );
    }

    @PatchMapping("/complaints/{id}/review")
    @Operation(summary = "Review complaint")
    public ResponseEntity<Response<ComplaintResponse>> reviewComplaint(
            @PathVariable Long id,
            @Valid @RequestBody ReviewComplaintRequest request
    ) {
        return ResponseEntity.ok(
                Response.ok("Complaint reviewed successfully", complaintService.reviewComplaint(id, request))
        );
    }
}
