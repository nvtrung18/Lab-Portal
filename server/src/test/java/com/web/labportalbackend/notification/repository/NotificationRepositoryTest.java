package com.web.labportalbackend.notification.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.enums.UserStatus;
import com.web.labportalbackend.notification.entity.NotificationEntity;
import com.web.labportalbackend.notification.enums.NotificationEventType;
import com.web.labportalbackend.notification.enums.NotificationTargetModule;
import java.time.Instant;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

@DataJpaTest
class NotificationRepositoryTest {

    @Autowired UserRepository userRepository;
    @Autowired NotificationRepository notificationRepository;

    @Test
    void notificationListingAndLookupAreOwnerScoped() {
        User owner = saveUser("notification-owner");
        User other = saveUser("notification-other");
        NotificationEntity older = save(owner, NotificationEventType.TASK_ASSIGNED,
                NotificationTargetModule.TASK, 10L, null, Instant.parse("2026-08-01T00:00:00Z"));
        NotificationEntity newer = save(owner, NotificationEventType.AI_ACTION_STATUS_CHANGED,
                NotificationTargetModule.AI, 20L, AiAssistantKey.RESEARCH_ASSISTANT,
                Instant.parse("2026-08-02T00:00:00Z"));
        save(other, NotificationEventType.FACE_CHECKIN_SUCCEEDED,
                NotificationTargetModule.FACE, 30L, null, Instant.parse("2026-08-03T00:00:00Z"));

        var page = notificationRepository.findByRecipientIdOrderByCreatedAtDescIdDesc(
                owner.getId(), PageRequest.of(0, 10));

        assertEquals(2, page.getTotalElements());
        assertEquals(newer.getId(), page.getContent().get(0).getId());
        assertEquals(older.getId(), page.getContent().get(1).getId());
        assertTrue(notificationRepository.findByIdAndRecipientId(newer.getId(), owner.getId()).isPresent());
        assertFalse(notificationRepository.findByIdAndRecipientId(newer.getId(), other.getId()).isPresent());
        assertEquals(AiAssistantKey.RESEARCH_ASSISTANT, page.getContent().get(0).getAssistantKey());
    }

    @Test
    void unreadCountChangesOnlyAfterOwnerNotificationIsMarkedRead() {
        User owner = saveUser("unread-owner");
        NotificationEntity notification = save(owner, NotificationEventType.REPORT_SUBMITTED,
                NotificationTargetModule.REPORT, 40L, null, Instant.parse("2026-08-01T00:00:00Z"));

        assertEquals(1, notificationRepository.countByRecipientIdAndReadFalse(owner.getId()));
        notification.markRead();
        notificationRepository.saveAndFlush(notification);
        assertEquals(0, notificationRepository.countByRecipientIdAndReadFalse(owner.getId()));
    }

    private User saveUser(String name) {
        return userRepository.saveAndFlush(new User(name + "@example.test", name, "password", name,
                null, UserStatus.ACTIVE, new HashSet<>()));
    }

    private NotificationEntity save(
            User recipient,
            NotificationEventType eventType,
            NotificationTargetModule targetModule,
            Long targetId,
            AiAssistantKey assistantKey,
            Instant createdAt
    ) {
        return notificationRepository.saveAndFlush(NotificationEntity.builder()
                .recipient(recipient)
                .eventType(eventType)
                .title("Notification title")
                .message("Notification message")
                .targetModule(targetModule)
                .targetId(targetId)
                .assistantKey(assistantKey)
                .createdAt(createdAt)
                .build());
    }
}
