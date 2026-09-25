package com.web.labportalbackend.lab.controller;

import com.web.labportalbackend.common.network.ClientIpResolver;
import com.web.labportalbackend.common.dto.Response;
import com.web.labportalbackend.lab.dto.request.AddLabAllowedNetworkRequest;
import com.web.labportalbackend.lab.dto.request.LabCheckinPolicyRequest;
import com.web.labportalbackend.lab.dto.response.LabAllowedNetworkResponse;
import com.web.labportalbackend.lab.dto.response.LabCheckinPolicyResponse;
import com.web.labportalbackend.lab.service.LabCheckinPolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/labs/{labId}/checkin-policy")
@RequiredArgsConstructor
@SecurityRequirement(name = "Bearer Authentication")
@PreAuthorize("hasAnyRole('ADMIN', 'LAB_MANAGER')")
public class LabCheckinPolicyController {

    private final LabCheckinPolicyService policyService;
    private final ClientIpResolver clientIpResolver;

    @GetMapping
    @Operation(summary = "Get laboratory self check-in policy")
    public ResponseEntity<Response<LabCheckinPolicyResponse>> getPolicy(@PathVariable Long labId) {
        return ResponseEntity.ok(Response.ok(policyService.getPolicy(labId)));
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update laboratory self check-in policy")
    public ResponseEntity<Response<LabCheckinPolicyResponse>> updatePolicy(
            @PathVariable Long labId,
            @Valid @RequestBody LabCheckinPolicyRequest request) {
        return ResponseEntity.ok(Response.ok("Laboratory check-in policy updated", policyService.updatePolicy(labId, request)));
    }

    @PostMapping("/networks")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Add an IP or CIDR network to the laboratory allowlist")
    public ResponseEntity<Response<LabAllowedNetworkResponse>> addNetwork(
            @PathVariable Long labId,
            @Valid @RequestBody AddLabAllowedNetworkRequest request) {
        return ResponseEntity.ok(Response.ok("Laboratory network added", policyService.addNetwork(labId, request)));
    }

    @PostMapping("/networks/current")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Register the current request IP for the laboratory")
    public ResponseEntity<Response<LabAllowedNetworkResponse>> registerCurrentNetwork(
            @PathVariable Long labId,
            HttpServletRequest request) {
        String currentIp = clientIpResolver.resolve(request);
        return ResponseEntity.ok(Response.ok("Current laboratory network registered",
                policyService.registerCurrentNetwork(labId, currentIp)));
    }

    @DeleteMapping("/networks/{networkId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Disable a laboratory allowlisted network")
    public ResponseEntity<Response<Void>> disableNetwork(
            @PathVariable Long labId,
            @PathVariable Long networkId) {
        policyService.disableNetwork(labId, networkId);
        return ResponseEntity.ok(Response.ok("Laboratory network disabled"));
    }
}
