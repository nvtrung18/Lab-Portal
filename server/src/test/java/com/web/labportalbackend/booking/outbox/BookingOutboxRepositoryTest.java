package com.web.labportalbackend.booking.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class BookingOutboxRepositoryTest {

    @Autowired BookingOutboxRepository repository;

    @Test
    void claimsOnlyReadyOrStaleEventsInDeterministicOrder() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        BookingOutboxEvent first = save("00000000-0000-0000-0000-000000000001",
                OutboxStatus.PENDING, now.minusSeconds(2), null, now.minusSeconds(3));
        save("00000000-0000-0000-0000-000000000002",
                OutboxStatus.PENDING, now.plusSeconds(60), null, now.minusSeconds(2));
        BookingOutboxEvent stale = save("00000000-0000-0000-0000-000000000003",
                OutboxStatus.PROCESSING, now, now.minusSeconds(120), now.minusSeconds(1));
        save("00000000-0000-0000-0000-000000000004",
                OutboxStatus.DELIVERED, now.minusSeconds(2), null, now);

        List<BookingOutboxEvent> ready = repository.findReadyForUpdate(
                now, now.minusSeconds(60), PageRequest.of(0, 10));

        assertThat(ready).extracting(BookingOutboxEvent::getEventId)
                .containsExactly(first.getEventId(), stale.getEventId());
    }

    private BookingOutboxEvent save(
            String id, OutboxStatus status, Instant nextAttemptAt, Instant lockedAt, Instant createdAt) {
        BookingOutboxEvent event = new BookingOutboxEvent();
        event.setEventId(id);
        event.setAggregateId(11L);
        event.setEventType(BookingOutboxService.BOOKING_EMAIL_REQUESTED);
        event.setEventVersion(1);
        event.setPayloadJson("{}");
        event.setStatus(status);
        event.setAttemptCount(0);
        event.setNextAttemptAt(nextAttemptAt);
        event.setLockedAt(lockedAt);
        event.setDeliveredAt(status == OutboxStatus.DELIVERED ? createdAt : null);
        event.setCreatedAt(createdAt);
        event.setUpdatedAt(createdAt);
        return repository.saveAndFlush(event);
    }
}
