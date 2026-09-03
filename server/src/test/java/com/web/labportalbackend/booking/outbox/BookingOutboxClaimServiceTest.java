package com.web.labportalbackend.booking.outbox;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BookingOutboxClaimServiceTest {

    @Test
    void claimMarksReadyEventsProcessingAndIncrementsAttempt() {
        BookingOutboxRepository repository = mock(BookingOutboxRepository.class);
        BookingOutboxEvent event = pendingEvent(0);
        when(repository.findReadyForUpdate(any(), any(), any())).thenReturn(List.of(event));
        when(repository.saveAllAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        BookingOutboxClaimService service = new BookingOutboxClaimService(repository);

        List<BookingOutboxEvent> claimed = service.claim(25, Duration.ofMinutes(1));

        assertThat(claimed).containsExactly(event);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PROCESSING);
        assertThat(event.getAttemptCount()).isEqualTo(1);
        assertThat(event.getLockedAt()).isNotNull();
    }

    @Test
    void failureReturnsToPendingUntilAttemptsAreExhausted() {
        BookingOutboxRepository repository = mock(BookingOutboxRepository.class);
        BookingOutboxEvent event = pendingEvent(2);
        event.setStatus(OutboxStatus.PROCESSING);
        when(repository.findById("event-id")).thenReturn(Optional.of(event));
        BookingOutboxClaimService service = new BookingOutboxClaimService(repository);

        OutboxStatus retryStatus = service.markFailed(
                "event-id", 3, Duration.ofSeconds(5), "IllegalStateException");
        assertThat(retryStatus).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getNextAttemptAt()).isAfter(Instant.now());

        event.setStatus(OutboxStatus.PROCESSING);
        event.setAttemptCount(3);
        OutboxStatus terminalStatus = service.markFailed(
                "event-id", 3, Duration.ofSeconds(5), "IllegalStateException");
        assertThat(terminalStatus).isEqualTo(OutboxStatus.FAILED);
    }

    private BookingOutboxEvent pendingEvent(int attempts) {
        BookingOutboxEvent event = new BookingOutboxEvent();
        event.setEventId("event-id");
        event.setStatus(OutboxStatus.PENDING);
        event.setAttemptCount(attempts);
        event.setCreatedAt(Instant.now());
        event.setUpdatedAt(Instant.now());
        event.setNextAttemptAt(Instant.now());
        return event;
    }
}
