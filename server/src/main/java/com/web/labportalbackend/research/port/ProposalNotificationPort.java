package com.web.labportalbackend.research.port;

public interface ProposalNotificationPort {

    void publish(ProposalNotificationEvent event);
}
