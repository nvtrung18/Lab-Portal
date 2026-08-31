package com.web.labportalbackend.notification.dto;

import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.notification.enums.NotificationEventType;
import com.web.labportalbackend.notification.enums.NotificationTargetModule;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record NotificationResponse(
        @Schema(description = "Unique notification identifier") Long id,
        @Schema(description = "Event that produced the notification") NotificationEventType eventType,
        @Schema(description = "Short notification heading") String title,
        @Schema(description = "Notification detail shown to the recipient") String message,
        @Schema(description = "Application module containing the related resource") NotificationTargetModule targetModule,
        @Schema(description = "Identifier of the related resource when one exists") Long targetId,
        @Schema(description = "AI assistant associated with an AI notification, otherwise absent")
        AiAssistantKey assistantKey,
        @Schema(description = "Whether the recipient has marked the notification as read") boolean read,
        @Schema(description = "Time when the notification was created") Instant createdAt
) {
}
