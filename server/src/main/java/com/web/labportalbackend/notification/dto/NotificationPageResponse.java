package com.web.labportalbackend.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record NotificationPageResponse(
        @Schema(description = "Notifications in the requested page") List<NotificationResponse> items,
        @Schema(description = "Zero-based page number") int page,
        @Schema(description = "Maximum number of notifications in the page") int size,
        @Schema(description = "Total notifications owned by the recipient") long totalElements,
        @Schema(description = "Total pages available for the recipient") int totalPages,
        @Schema(description = "Current number of unread notifications owned by the recipient") long unreadCount
) {
}
