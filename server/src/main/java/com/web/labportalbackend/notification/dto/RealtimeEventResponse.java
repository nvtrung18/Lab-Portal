package com.web.labportalbackend.notification.dto;

import com.web.labportalbackend.notification.enums.NotificationEventType;
import com.web.labportalbackend.notification.enums.NotificationTargetModule;
import com.web.labportalbackend.notification.enums.RealtimeEventType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record RealtimeEventResponse(
        @Schema(description = "Unique identifier used to distinguish this realtime event") String eventId,
        @Schema(description = "Kind of realtime update delivered to the authenticated recipient")
        RealtimeEventType type,
        @Schema(description = "Notification event that caused this update, when applicable")
        NotificationEventType notificationEventType,
        @Schema(description = "Short recipient-scoped notification title, when applicable") String title,
        @Schema(description = "Recipient-scoped notification message, when applicable") String message,
        @Schema(description = "Application module containing the changed resource, when applicable")
        NotificationTargetModule targetModule,
        @Schema(description = "Identifier of the changed resource, when one exists") Long targetId,
        @Schema(description = "Time when the realtime event was created") Instant occurredAt
) {
}
