package com.web.labportalbackend.booking.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
public class BookingOutboxRelay {

    private final BookingOutboxRepository repository;
    private final BookingOutboxClaimService claimService;
    private final BookingOutboxEventDispatcher dispatcher;
    private final MeterRegistry meterRegistry;
    private final int batchSize;
    private final int maxAttempts;
    private final Duration retryDelay;
    private final Duration lockTimeout;
    private final Duration retention;

    public BookingOutboxRelay(
            BookingOutboxRepository repository,
            BookingOutboxClaimService claimService,
            BookingOutboxEventDispatcher dispatcher,
            MeterRegistry meterRegistry,
            @Value("${booking.outbox.batch-size:25}") int batchSize,
            @Value("${booking.outbox.max-attempts:8}") int maxAttempts,
            @Value("${booking.outbox.retry-delay-ms:5000}") long retryDelayMillis,
            @Value("${booking.outbox.lock-timeout-ms:60000}") long lockTimeoutMillis,
            @Value("${booking.outbox.retention-days:7}") long retentionDays
    ) {
        this.repository = repository;
        this.claimService = claimService;
        this.dispatcher = dispatcher;
        this.meterRegistry = meterRegistry;
        this.batchSize = Math.max(1, batchSize);
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryDelay = Duration.ofMillis(Math.max(0, retryDelayMillis));
        this.lockTimeout = Duration.ofMillis(Math.max(1000, lockTimeoutMillis));
        this.retention = Duration.ofDays(Math.max(1, retentionDays));
        registerBacklogGauge(OutboxStatus.PENDING);
        registerBacklogGauge(OutboxStatus.FAILED);
    }

    @Scheduled(fixedDelayString = "${booking.outbox.poll-delay-ms:1000}")
    public void relay() {
        for (BookingOutboxEvent event : claimService.claim(batchSize, lockTimeout)) {
            deliver(event);
        }
    }

    @Scheduled(cron = "${booking.outbox.cleanup-cron:0 30 2 * * *}")
    public void cleanup() {
        int deleted = claimService.deleteDeliveredBefore(Instant.now().minus(retention));
        if (deleted > 0) {
            log.info("Deleted {} delivered booking outbox event(s)", deleted);
        }
    }

    private void deliver(BookingOutboxEvent event) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "delivered";
        try {
            dispatcher.dispatch(event);
            claimService.markDelivered(event.getEventId());
        } catch (RuntimeException exception) {
            OutboxStatus status = claimService.markFailed(event.getEventId(), maxAttempts, retryDelay,
                    exception.getClass().getSimpleName());
            outcome = status == OutboxStatus.FAILED ? "failed" : "retry";
            meterRegistry.counter("booking.outbox.delivery.failures", "outcome", outcome).increment();
            log.warn("Booking outbox delivery failed for eventId={}, attempt={}, outcome={}, failureType={}",
                    event.getEventId(), event.getAttemptCount(), outcome, exception.getClass().getSimpleName());
        } finally {
            sample.stop(meterRegistry.timer("booking.outbox.processing.duration", "outcome", outcome));
        }
    }

    private void registerBacklogGauge(OutboxStatus status) {
        Gauge.builder("booking.outbox.backlog", repository, value -> value.countByStatus(status))
                .tag("status", status.name().toLowerCase())
                .register(meterRegistry);
    }
}
