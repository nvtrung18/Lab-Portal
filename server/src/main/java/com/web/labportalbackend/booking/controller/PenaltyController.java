package com.web.labportalbackend.booking.controller;

import com.web.labportalbackend.booking.dto.request.PenaltyConfigRequest;
import com.web.labportalbackend.booking.dto.response.PenaltyConfigResponse;
import com.web.labportalbackend.booking.dto.response.PenaltyResponse;
import com.web.labportalbackend.booking.service.PenaltyService;
import com.web.labportalbackend.common.dto.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Penalty", description = "Penalty configuration and history endpoints")
public class PenaltyController {

    private final PenaltyService penaltyService;

    @PutMapping("/config/penalty")
    @Operation(summary = "Configure no-show penalty amount")
    public ResponseEntity<Response<PenaltyConfigResponse>> updatePenaltyConfig(
            @Valid @RequestBody PenaltyConfigRequest request
    ) {
        return ResponseEntity.ok(
                Response.ok("Penalty configuration updated", penaltyService.updateConfig(request))
        );
    }

    @GetMapping("/users/{id}/penalties")
    @Operation(summary = "Get user penalty history")
    public ResponseEntity<Response<List<PenaltyResponse>>> getUserPenalties(@PathVariable Long id) {
        return ResponseEntity.ok(
                Response.ok("User penalties retrieved successfully", penaltyService.getUserPenalties(id))
        );
    }
}
