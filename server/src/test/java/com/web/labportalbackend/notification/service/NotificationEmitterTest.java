package com.web.labportalbackend.notification.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.notification.enums.NotificationEventType;
import com.web.labportalbackend.notification.enums.NotificationTargetModule;
import com.web.labportalbackend.notification.repository.NotificationRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationEmitterTest {

    @Mock NotificationRepository notificationRepository;
    @Mock UserRepository userRepository;

    private NotificationEmitter emitter;

    @BeforeEach
    void setUp() {
        emitter = new NotificationEmitter(notificationRepository, userRepository);
    }

    @Test
    void persistsOnlyForResolvedActiveRecipient() {
        User recipient = new User();
        recipient.setId(7L);
        recipient.setActive(true);
        recipient.setDeleted(false);
        when(userRepository.findById(7L)).thenReturn(Optional.of(recipient));

        emitter.emit(7L, NotificationEventType.TASK_ASSIGNED, "Task assigned",
                "A research task was assigned to you", NotificationTargetModule.TASK, 42L, null);

        verify(notificationRepository).save(argThat(notification ->
                notification.getRecipient() == recipient
                        && notification.getTargetId().equals(42L)
                        && notification.getEventType() == NotificationEventType.TASK_ASSIGNED));
    }

    @Test
    void rejectsMissingRecipientInsteadOfCreatingUnscopedNotification() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> emitter.emitToRecipients(
                List.of(99L), NotificationEventType.REPORT_REVIEWED, "Report reviewed",
                "Your report was reviewed", NotificationTargetModule.REPORT, 5L, null));

        verify(notificationRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
