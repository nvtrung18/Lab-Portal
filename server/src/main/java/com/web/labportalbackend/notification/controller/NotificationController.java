package com.web.labportalbackend.notification.controller;

import com.web.labportalbackend.common.dto.Response;
import com.web.labportalbackend.notification.dto.NotificationPageResponse;
import com.web.labportalbackend.notification.dto.NotificationResponse;
import com.web.labportalbackend.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Validated
@PreAuthorize("isAuthenticated()")
@Tag(name = "Notifications", description = "Notifications owned by the authenticated user")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "Get current user's notifications")
    public ResponseEntity<Response<NotificationPageResponse>> getNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return ResponseEntity.ok(Response.ok("Notifications retrieved",
                notificationService.getCurrentUserNotifications(pageable)));
    }

    @PatchMapping("/{notificationId}/read")
    @Operation(summary = "Mark one owned notification as read")
    public ResponseEntity<Response<NotificationResponse>> markRead(
            @PathVariable @Positive Long notificationId
    ) {
        return ResponseEntity.ok(Response.ok("Notification marked as read",
                notificationService.markCurrentUserNotificationRead(notificationId)));
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Mark all current user's notifications as read")
    public ResponseEntity<Response<Integer>> markAllRead() {
        return ResponseEntity.ok(Response.ok("Notifications marked as read",
                notificationService.markAllCurrentUserNotificationsRead()));
    }
}
