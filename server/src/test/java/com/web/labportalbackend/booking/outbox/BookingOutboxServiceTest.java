package com.web.labportalbackend.booking.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.labportalbackend.booking.event.BookingEmailType;
import com.web.labportalbackend.common.email.BookingEmailData;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BookingOutboxServiceTest {

    @Test
    void persistsVersionedTypedEvent() throws Exception {
        BookingOutboxRepository repository = mock(BookingOutboxRepository.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        BookingOutboxService service = new BookingOutboxService(repository, objectMapper);
        BookingEmailData data = BookingEmailData.builder()
                .studentName("Student")
                .labName("Lab A")
                .startTime(Instant.parse("2026-09-03T01:00:00Z"))
                .endTime(Instant.parse("2026-09-03T02:00:00Z"))
                .status("Approved")
                .build();

        service.enqueueEmail(11L, BookingEmailType.APPROVED, "student@example.com", data);

        ArgumentCaptor<BookingOutboxEvent> captor = ArgumentCaptor.forClass(BookingOutboxEvent.class);
        verify(repository).save(captor.capture());
        BookingOutboxEvent event = captor.getValue();
        assertThat(event.getAggregateId()).isEqualTo(11L);
        assertThat(event.getEventType()).isEqualTo(BookingOutboxService.BOOKING_EMAIL_REQUESTED);
        assertThat(event.getEventVersion()).isEqualTo(1);
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        BookingEmailPayload payload = objectMapper.readValue(event.getPayloadJson(), BookingEmailPayload.class);
        assertThat(payload.emailType()).isEqualTo(BookingEmailType.APPROVED);
        assertThat(payload.recipientEmail()).isEqualTo("student@example.com");
    }
}
