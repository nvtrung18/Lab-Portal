package com.web.labportalbackend.admin.operations.controller;

import com.web.labportalbackend.admin.operations.dto.AiActionOperationalResponse;
import com.web.labportalbackend.admin.operations.dto.AiUsageOperationalResponse;
import com.web.labportalbackend.admin.operations.dto.FaceCheckinOperationalResponse;
import com.web.labportalbackend.admin.operations.dto.OperationalLogPageResponse;
import com.web.labportalbackend.admin.operations.service.OperationalLogService;
import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.ai.enums.AiResourceType;
import com.web.labportalbackend.common.dto.Response;
import com.web.labportalbackend.face.enums.FaceCheckinResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/operational-logs")
@RequiredArgsConstructor
@Tag(name = "Operational Logs", description = "Filtered operational evidence with role and lab scoping")
public class OperationalLogController {

    private final OperationalLogService operationalLogService;

    @GetMapping("/ai-usage")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get filtered AI usage logs")
    public ResponseEntity<Response<OperationalLogPageResponse<AiUsageOperationalResponse>>> aiUsage(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) Long labId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(Response.ok("AI usage logs retrieved",
                operationalLogService.getAiUsage(userId, module, labId, from, to, page(page, size))));
    }

    @GetMapping("/ai-actions")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get filtered AI action logs")
    public ResponseEntity<Response<OperationalLogPageResponse<AiActionOperationalResponse>>> aiActions(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) AiAssistantKey assistantKey,
            @RequestParam(required = false) AiResourceType resourceType,
            @RequestParam(required = false) Long resourceId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(Response.ok("AI action logs retrieved",
                operationalLogService.getAiActions(userId, assistantKey, resourceType, resourceId,
                        from, to, page(page, size))));
    }

    @GetMapping("/face-checkins")
    @PreAuthorize("hasAnyRole('ADMIN','LAB_MANAGER')")
    @Operation(summary = "Get filtered face check-in logs within the authorized laboratory scope")
    public ResponseEntity<Response<OperationalLogPageResponse<FaceCheckinOperationalResponse>>> faceCheckins(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long labId,
            @RequestParam(required = false) Long bookingId,
            @RequestParam(required = false) FaceCheckinResult result,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(Response.ok("Face check-in logs retrieved",
                operationalLogService.getFaceCheckins(userId, labId, bookingId, result,
                        from, to, page(page, size))));
    }

    private PageRequest page(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }
}
