package com.web.labportalbackend.admin.lab.controller;

import com.web.labportalbackend.common.dto.Response;
import com.web.labportalbackend.lab.dto.request.AssignManagerRequest;
import com.web.labportalbackend.lab.dto.response.LabResponse;
import com.web.labportalbackend.lab.dto.response.AssignableLabResponse;
import com.web.labportalbackend.lab.service.LabService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/labs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminLabController {

    private final LabService labService;

    @GetMapping("/assignable")
    public ResponseEntity<Response<List<AssignableLabResponse>>> getAssignableLabs(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "false") Boolean includeInactive) {
        List<AssignableLabResponse> labs = labService.getAssignableLabs(keyword, includeInactive);
        return ResponseEntity.ok(Response.ok("Assignable laboratories retrieved successfully", labs));
    }

    @PatchMapping("/{labId}/manager")
    public ResponseEntity<Response<LabResponse>> assignManager(
            @PathVariable Long labId,
            @Valid @RequestBody AssignManagerRequest request) {
        LabResponse updatedLab = labService.assignManagerPatch(labId, request.getManagerUserId());
        return ResponseEntity.ok(Response.ok("Đã gán quản lý PTN thành công.", updatedLab));
    }
}
