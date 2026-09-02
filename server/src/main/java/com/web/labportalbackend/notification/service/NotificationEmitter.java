package com.web.labportalbackend.notification.service;

import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.notification.entity.NotificationEntity;
import com.web.labportalbackend.notification.event.NotificationCreatedEvent;
import com.web.labportalbackend.notification.enums.NotificationEventType;
import com.web.labportalbackend.notification.enums.NotificationTargetModule;
import com.web.labportalbackend.notification.repository.NotificationRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.Collection;
import java.util.LinkedHashSet;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationEmitter {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    public void emit(
            Long recipientId,
            NotificationEventType eventType,
            String title,
            String message,
            NotificationTargetModule targetModule,
            Long targetId,
            AiAssistantKey assistantKey
    ) {
        User recipient = userRepository.findById(recipientId)
                .filter(user -> Boolean.TRUE.equals(user.getActive()))
                .filter(user -> !Boolean.TRUE.equals(user.getDeleted()))
                .orElseThrow(() -> new EntityNotFoundException("Notification recipient not found"));
        NotificationEntity notification = notificationRepository.save(NotificationEntity.builder()
                .recipient(recipient)
                .eventType(eventType)
                .title(title)
                .message(message)
                .targetModule(targetModule)
                .targetId(targetId)
                .assistantKey(assistantKey)
                .build());
        eventPublisher.publishEvent(new NotificationCreatedEvent(
                notification.getId(), recipient.getId(), eventType, title, message,
                targetModule, targetId, notification.getCreatedAt()));
    }

    public void emitToRecipients(
            Collection<Long> recipientIds,
            NotificationEventType eventType,
            String title,
            String message,
            NotificationTargetModule targetModule,
            Long targetId,
            AiAssistantKey assistantKey
    ) {
        new LinkedHashSet<>(recipientIds).forEach(recipientId -> emit(
                recipientId, eventType, title, message, targetModule, targetId, assistantKey));
    }
}
