package com.web.labportalbackend.booking.service;

import com.web.labportalbackend.booking.event.BookingEmailRequestedEvent;
import com.web.labportalbackend.booking.event.BookingEmailType;
import com.web.labportalbackend.common.email.BookingEmailData;
import com.web.labportalbackend.common.email.EmailService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class BookingEmailListenerTest {

    @Test
    void retriesTransientFailureThenDelivers() {
        EmailService emailService = mock(EmailService.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        BookingEmailData data = BookingEmailData.builder().status("Approved").build();
        doThrow(new IllegalStateException("temporary failure"))
                .doNothing()
                .when(emailService).sendBookingApprovedEmail("student@example.com", data);
        BookingEmailListener listener = new BookingEmailListener(emailService, meterRegistry, 3, 0);

        listener.onBookingEmailRequested(new BookingEmailRequestedEvent(
                11L, BookingEmailType.APPROVED, "student@example.com", data));

        verify(emailService, times(2)).sendBookingApprovedEmail("student@example.com", data);
        assertThat(meterRegistry.counter("booking.side_effect.retries",
                "channel", "email", "type", "APPROVED").count()).isEqualTo(1);
        assertThat(meterRegistry.timer("booking.side_effect.delivery.duration",
                "channel", "email", "type", "APPROVED", "outcome", "success").count()).isEqualTo(1);
    }

    @Test
    void exhaustedFailureIsObservedWithoutEscapingListener() {
        EmailService emailService = mock(EmailService.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        BookingEmailData data = BookingEmailData.builder().status("Rejected").build();
        doThrow(new IllegalStateException("provider unavailable"))
                .when(emailService).sendBookingRejectedEmail("student@example.com", data);
        BookingEmailListener listener = new BookingEmailListener(emailService, meterRegistry, 2, 0);

        listener.onBookingEmailRequested(new BookingEmailRequestedEvent(
                12L, BookingEmailType.REJECTED, "student@example.com", data));

        verify(emailService, times(2)).sendBookingRejectedEmail("student@example.com", data);
        assertThat(meterRegistry.counter("booking.side_effect.failures",
                "channel", "email", "type", "REJECTED").count()).isEqualTo(1);
        assertThat(meterRegistry.timer("booking.side_effect.delivery.duration",
                "channel", "email", "type", "REJECTED", "outcome", "failed").count()).isEqualTo(1);
    }
}
