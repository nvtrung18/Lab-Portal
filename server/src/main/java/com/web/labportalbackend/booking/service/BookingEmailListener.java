package com.web.labportalbackend.booking.service;

import com.web.labportalbackend.booking.event.BookingEmailRequestedEvent;
import com.web.labportalbackend.common.email.EmailService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class BookingEmailListener {

    private final EmailService emailService;
    private final MeterRegistry meterRegistry;
    private final int maxAttempts;
    private final long retryDelayMillis;

    public BookingEmailListener(
            EmailService emailService,
            MeterRegistry meterRegistry,
            @Value("${booking.side-effects.email.max-attempts:3}") int maxAttempts,
            @Value("${booking.side-effects.email.retry-delay-ms:500}") long retryDelayMillis
    ) {
        this.emailService = emailService;
        this.meterRegistry = meterRegistry;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryDelayMillis = Math.max(0, retryDelayMillis);
    }

    @Async("sideEffectExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingEmailRequested(BookingEmailRequestedEvent event) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "success";
        try {
            deliverWithRetry(event);
        } catch (RuntimeException exception) {
            outcome = "failed";
            meterRegistry.counter("booking.side_effect.failures",
                    "channel", "email", "type", event.type().name()).increment();
            log.error("Booking email delivery exhausted retries for bookingId={}, type={}, failureType={}",
                    event.bookingId(), event.type(), exception.getClass().getSimpleName());
        } finally {
            sample.stop(meterRegistry.timer("booking.side_effect.delivery.duration",
                    "channel", "email", "type", event.type().name(), "outcome", outcome));
        }
    }

    private void deliverWithRetry(BookingEmailRequestedEvent event) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                deliver(event);
                return;
            } catch (RuntimeException exception) {
                lastFailure = exception;
                if (attempt < maxAttempts) {
                    meterRegistry.counter("booking.side_effect.retries",
                            "channel", "email", "type", event.type().name()).increment();
                    log.warn("Retrying booking email for bookingId={}, type={}, attempt={}/{}",
                            event.bookingId(), event.type(), attempt, maxAttempts);
                    if (!waitBeforeRetry()) {
                        break;
                    }
                }
            }
        }
        throw lastFailure == null ? new IllegalStateException("Booking email delivery failed") : lastFailure;
    }

    private void deliver(BookingEmailRequestedEvent event) {
        switch (event.type()) {
            case CREATED -> emailService.sendBookingCreatedEmail(event.recipientEmail(), event.data());
            case APPROVED -> emailService.sendBookingApprovedEmail(event.recipientEmail(), event.data());
            case REJECTED -> emailService.sendBookingRejectedEmail(event.recipientEmail(), event.data());
            case CANCELLED_BY_STUDENT ->
                    emailService.sendBookingCancelledByStudentEmail(event.recipientEmail(), event.data());
        }
    }

    private boolean waitBeforeRetry() {
        if (retryDelayMillis == 0) {
            return true;
        }
        try {
            Thread.sleep(retryDelayMillis);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
