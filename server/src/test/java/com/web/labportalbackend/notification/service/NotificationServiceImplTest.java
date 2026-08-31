package com.web.labportalbackend.notification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.notification.entity.NotificationEntity;
import com.web.labportalbackend.notification.enums.NotificationEventType;
import com.web.labportalbackend.notification.enums.NotificationTargetModule;
import com.web.labportalbackend.notification.repository.NotificationRepository;
import com.web.labportalbackend.notification.service.impl.NotificationServiceImpl;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock NotificationRepository notificationRepository;
    @Mock UserRepository userRepository;

    private NotificationServiceImpl service;
    private User recipient;

    @BeforeEach
    void setUp() {
        service = new NotificationServiceImpl(notificationRepository, userRepository);
        recipient = new User();
        recipient.setId(7L);
        recipient.setUsername("student");
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("student", "n/a", java.util.List.of()));
        when(userRepository.findByUsername("student")).thenReturn(Optional.of(recipient));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void recipientCanMarkOwnedNotificationRead() {
        NotificationEntity notification = NotificationEntity.builder()
                .id(11L)
                .recipient(recipient)
                .eventType(NotificationEventType.TASK_ASSIGNED)
                .title("Task assigned")
                .message("A task was assigned to you")
                .targetModule(NotificationTargetModule.TASK)
                .targetId(42L)
                .read(false)
                .createdAt(Instant.parse("2026-08-31T00:00:00Z"))
                .build();
        when(notificationRepository.findByIdAndRecipientId(11L, 7L)).thenReturn(Optional.of(notification));

        var response = service.markCurrentUserNotificationRead(11L);

        assertEquals(11L, response.id());
        assertEquals(true, response.read());
        verify(notificationRepository).findByIdAndRecipientId(11L, 7L);
    }

    @Test
    void recipientCannotReadNotificationOutsideOwnerScope() {
        when(notificationRepository.findByIdAndRecipientId(99L, 7L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> service.markCurrentUserNotificationRead(99L));

        verify(notificationRepository).findByIdAndRecipientId(99L, 7L);
        verify(notificationRepository, never()).findById(99L);
    }

    @Test
    void markAllUsesRecipientScopedBulkUpdate() {
        when(notificationRepository.markAllReadByRecipientId(7L)).thenReturn(3);

        assertEquals(3, service.markAllCurrentUserNotificationsRead());

        verify(notificationRepository).markAllReadByRecipientId(7L);
    }
}
