package com.web.labportalbackend;

import com.web.labportalbackend.research.adapter.NoOpProposalNotificationAdapter;
import com.web.labportalbackend.research.port.ProposalNotificationPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@SpringBootTest
class LabPortalBackendApplicationTests {

    @Autowired
    private List<ProposalNotificationPort> proposalNotificationPorts;

    @Test
    void contextLoads() {
        assertEquals(1, proposalNotificationPorts.size());
        assertInstanceOf(NoOpProposalNotificationAdapter.class, proposalNotificationPorts.getFirst());
    }

}
