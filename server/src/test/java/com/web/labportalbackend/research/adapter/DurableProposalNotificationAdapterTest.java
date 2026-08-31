package com.web.labportalbackend.research.adapter;

import static org.mockito.Mockito.verify;

import com.web.labportalbackend.notification.enums.NotificationEventType;
import com.web.labportalbackend.notification.enums.NotificationTargetModule;
import com.web.labportalbackend.notification.service.NotificationEmitter;
import com.web.labportalbackend.research.port.ProposalNotificationEvent;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DurableProposalNotificationAdapterTest {

    @Mock NotificationEmitter notificationEmitter;

    @Test
    void mapsApprovedProposalToDurableRecipientScopedNotification() {
        var adapter = new DurableProposalNotificationAdapter(notificationEmitter);
        var event = new ProposalNotificationEvent(
                1,
                ProposalNotificationEvent.ProposalNotificationType.APPROVED,
                15L,
                2L,
                3L,
                4L,
                List.of(7L),
                19L,
                Instant.parse("2026-08-31T00:00:00Z")
        );

        adapter.publish(event);

        verify(notificationEmitter).emitToRecipients(
                List.of(7L),
                NotificationEventType.PROPOSAL_APPROVED,
                "Task proposal approved",
                "Task proposal 15 was approved",
                NotificationTargetModule.PROPOSAL,
                15L,
                null
        );
    }
}
