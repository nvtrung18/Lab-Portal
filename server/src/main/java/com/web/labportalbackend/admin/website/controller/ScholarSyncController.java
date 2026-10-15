package com.web.labportalbackend.admin.website.controller;

import com.web.labportalbackend.admin.website.scholar.ScholarSyncService;
import com.web.labportalbackend.common.dto.Response;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/website/scholar-sync")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class ScholarSyncController {
    private final ScholarSyncService service;
    @GetMapping
    public ResponseEntity<Response<ScholarSyncService.Status>> status() {
        return ResponseEntity.ok(Response.ok("Scholar sync status retrieved", service.status()));
    }
    @PostMapping("/{authorId}")
    public ResponseEntity<Response<Void>> sync(@PathVariable String authorId) {
        service.syncNow(authorId);
        return ResponseEntity.accepted().body(Response.ok("Scholar sync requested. Check the sync status.", null));
    }
}
