package com.web.labportalbackend.admin.dashboard.controller;

import com.web.labportalbackend.admin.dashboard.dto.AdminDashboardStatsResponse;
import com.web.labportalbackend.admin.dashboard.service.AdminDashboardService;
import com.web.labportalbackend.common.dto.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Dashboard", description = "System-wide dashboard statistics for administrators")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @GetMapping("/stats")
    @Operation(summary = "Get admin dashboard stats")
    public ResponseEntity<Response<AdminDashboardStatsResponse>> getStats() {
        return ResponseEntity.ok(Response.ok("Admin dashboard stats retrieved", adminDashboardService.getStats()));
    }
}
