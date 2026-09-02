package com.web.labportalbackend.notification.service;

import com.web.labportalbackend.notification.event.NotificationCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class RealtimeNotificationListener {

    private final RealtimeEventService realtimeEventService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onNotificationCreated(NotificationCreatedEvent event) {
        realtimeEventService.publish(event);
    }
}
