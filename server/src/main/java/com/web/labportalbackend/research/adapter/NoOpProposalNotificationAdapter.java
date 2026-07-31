package com.web.labportalbackend.research.adapter;

import com.web.labportalbackend.research.port.ProposalNotificationEvent;
import com.web.labportalbackend.research.port.ProposalNotificationPort;
import org.springframework.stereotype.Component;

/**
 * Phase 3 stub. Phase 10 must remove this bean registration when it adds the
 * real in-transaction proposal notification adapter.
 */
@Component
public class NoOpProposalNotificationAdapter implements ProposalNotificationPort {

    @Override
    public void publish(ProposalNotificationEvent event) {
        // Intentionally no-op until Phase 10 provides durable notification persistence.
    }
}
