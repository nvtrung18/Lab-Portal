package com.web.labportalbackend.admin.systemconfig.controller;

import com.web.labportalbackend.admin.systemconfig.dto.SystemConfigRequest;
import com.web.labportalbackend.admin.systemconfig.dto.SystemConfigResponse;
import com.web.labportalbackend.admin.systemconfig.service.SystemConfigService;
import com.web.labportalbackend.common.dto.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/system-config")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin System Config", description = "Global system configuration for administrators")
public class SystemConfigController {

    private final SystemConfigService systemConfigService;

    @GetMapping
    @Operation(summary = "Get global system configuration")
    public ResponseEntity<Response<SystemConfigResponse>> getConfig() {
        return ResponseEntity.ok(Response.ok("System config retrieved", systemConfigService.getConfig()));
    }

    @PutMapping
    @Operation(summary = "Update global system configuration")
    public ResponseEntity<Response<SystemConfigResponse>> updateConfig(
            @Valid @RequestBody SystemConfigRequest request
    ) {
        return ResponseEntity.ok(Response.ok("System config updated", systemConfigService.updateConfig(request)));
    }
}
