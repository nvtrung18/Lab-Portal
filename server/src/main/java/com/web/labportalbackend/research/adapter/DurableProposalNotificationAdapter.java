package com.web.labportalbackend.research.adapter;

import com.web.labportalbackend.notification.enums.NotificationEventType;
import com.web.labportalbackend.notification.enums.NotificationTargetModule;
import com.web.labportalbackend.notification.service.NotificationEmitter;
import com.web.labportalbackend.research.port.ProposalNotificationEvent;
import com.web.labportalbackend.research.port.ProposalNotificationPort;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DurableProposalNotificationAdapter implements ProposalNotificationPort {

    private final NotificationEmitter notificationEmitter;

    @Override
    public void publish(ProposalNotificationEvent event) {
        NotificationEventType eventType = switch (event.type()) {
            case SUBMITTED -> NotificationEventType.PROPOSAL_SUBMITTED;
            case APPROVED -> NotificationEventType.PROPOSAL_APPROVED;
            case REJECTED -> NotificationEventType.PROPOSAL_REJECTED;
        };
        String outcome = event.type().name().toLowerCase(Locale.ROOT);
        notificationEmitter.emitToRecipients(
                event.recipientUserIds(),
                eventType,
                "Task proposal " + outcome,
                "Task proposal " + event.proposalId() + " was " + outcome,
                NotificationTargetModule.PROPOSAL,
                event.proposalId(),
                null
        );
    }
}
