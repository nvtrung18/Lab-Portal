package com.web.labportalbackend.notification.event;

import com.web.labportalbackend.notification.enums.NotificationEventType;
import com.web.labportalbackend.notification.enums.NotificationTargetModule;
import java.time.Instant;

public record NotificationCreatedEvent(
        Long notificationId,
        Long recipientId,
        NotificationEventType eventType,
        String title,
        String message,
        NotificationTargetModule targetModule,
        Long targetId,
        Instant occurredAt
) {
}
