package com.web.labportalbackend.booking.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.labportalbackend.booking.event.BookingEmailType;
import com.web.labportalbackend.common.email.BookingEmailData;
import com.web.labportalbackend.common.email.EmailService;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BookingOutboxEventDispatcherTest {

    @Test
    void dispatchesSupportedVersionToTypedEmailMethod() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        EmailService emailService = mock(EmailService.class);
        BookingOutboxEventDispatcher dispatcher = new BookingOutboxEventDispatcher(objectMapper, emailService);
        BookingEmailPayload payload = new BookingEmailPayload(
                "student@example.com", BookingEmailType.CREATED, "Student", "Lab A",
                Instant.parse("2026-09-03T01:00:00Z"), Instant.parse("2026-09-03T02:00:00Z"),
                "Pending", null);
        BookingOutboxEvent event = event(objectMapper.writeValueAsString(payload), 1);

        dispatcher.dispatch(event);

        verify(emailService).sendBookingCreatedEmail(
                org.mockito.ArgumentMatchers.eq("student@example.com"),
                argThat((BookingEmailData data) -> "Lab A".equals(data.getLabName())));
    }

    @Test
    void rejectsUnknownContractVersion() {
        BookingOutboxEventDispatcher dispatcher = new BookingOutboxEventDispatcher(
                new ObjectMapper().findAndRegisterModules(), mock(EmailService.class));

        assertThatThrownBy(() -> dispatcher.dispatch(event("{}", 2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported");
    }

    private BookingOutboxEvent event(String payload, int version) {
        BookingOutboxEvent event = new BookingOutboxEvent();
        event.setEventId("event-id");
        event.setEventType(BookingOutboxService.BOOKING_EMAIL_REQUESTED);
        event.setEventVersion(version);
        event.setPayloadJson(payload);
        return event;
    }
}
