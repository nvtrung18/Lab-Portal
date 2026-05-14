package com.web.labportalbackend.booking.controller;

import com.web.labportalbackend.booking.dto.request.ComplaintRequest;
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

@RestController
@RequestMapping("/complaints")
@RequiredArgsConstructor
@Tag(name = "Complaint", description = "User complaint endpoints")
public class ComplaintController {

    private final ComplaintService complaintService;

    @PostMapping
    @Operation(summary = "Submit complaint")
    public ResponseEntity<Response<ComplaintResponse>> submitComplaint(
            @Valid @RequestBody ComplaintRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.ok("Complaint submitted successfully", complaintService.submitComplaint(request)));
    }
}
