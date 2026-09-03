package com.web.labportalbackend.booking.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookingOutboxClaimService {

    private final BookingOutboxRepository repository;

    @Transactional
    public List<BookingOutboxEvent> claim(int batchSize, Duration lockTimeout) {
        Instant now = Instant.now();
        List<BookingOutboxEvent> events = repository.findReadyForUpdate(
                now, now.minus(lockTimeout), PageRequest.of(0, Math.max(1, batchSize)));
        events.forEach(event -> {
            event.setStatus(OutboxStatus.PROCESSING);
            event.setAttemptCount(event.getAttemptCount() + 1);
            event.setLockedAt(now);
            event.setUpdatedAt(now);
            event.setLastErrorCode(null);
        });
        return repository.saveAllAndFlush(events);
    }

    @Transactional
    public void markDelivered(String eventId) {
        repository.findById(eventId).filter(event -> event.getStatus() == OutboxStatus.PROCESSING)
                .ifPresent(event -> {
                    Instant now = Instant.now();
                    event.setStatus(OutboxStatus.DELIVERED);
                    event.setDeliveredAt(now);
                    event.setLockedAt(null);
                    event.setUpdatedAt(now);
                });
    }

    @Transactional
    public OutboxStatus markFailed(String eventId, int maxAttempts, Duration retryDelay, String errorCode) {
        return repository.findById(eventId).filter(event -> event.getStatus() == OutboxStatus.PROCESSING)
                .map(event -> {
                    Instant now = Instant.now();
                    boolean exhausted = event.getAttemptCount() >= Math.max(1, maxAttempts);
                    event.setStatus(exhausted ? OutboxStatus.FAILED : OutboxStatus.PENDING);
                    event.setNextAttemptAt(now.plus(retryDelay));
                    event.setLockedAt(null);
                    event.setUpdatedAt(now);
                    event.setLastErrorCode(errorCode);
                    return event.getStatus();
                })
                .orElse(OutboxStatus.FAILED);
    }

    @Transactional
    public int deleteDeliveredBefore(Instant cutoff) {
        return repository.deleteDeliveredBefore(OutboxStatus.DELIVERED, cutoff);
    }
}
