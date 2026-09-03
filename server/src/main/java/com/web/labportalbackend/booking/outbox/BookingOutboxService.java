package com.web.labportalbackend.booking.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.labportalbackend.booking.event.BookingEmailType;
import com.web.labportalbackend.common.email.BookingEmailData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingOutboxService {

    public static final String BOOKING_EMAIL_REQUESTED = "BOOKING_EMAIL_REQUESTED";
    public static final int CURRENT_VERSION = 1;

    private final BookingOutboxRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public String enqueueEmail(Long bookingId, BookingEmailType type, String email, BookingEmailData data) {
        BookingEmailPayload payload = new BookingEmailPayload(
                email, type, data.getStudentName(), data.getLabName(), data.getStartTime(),
                data.getEndTime(), data.getStatus(), data.getNote());
        Instant now = Instant.now();
        BookingOutboxEvent event = new BookingOutboxEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setAggregateId(bookingId);
        event.setEventType(BOOKING_EMAIL_REQUESTED);
        event.setEventVersion(CURRENT_VERSION);
        event.setPayloadJson(writePayload(payload));
        event.setStatus(OutboxStatus.PENDING);
        event.setAttemptCount(0);
        event.setNextAttemptAt(now);
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        repository.save(event);
        return event.getEventId();
    }

    private String writePayload(BookingEmailPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize booking outbox payload", exception);
        }
    }
}
