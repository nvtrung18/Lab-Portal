package com.web.labportalbackend.booking.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.labportalbackend.common.email.BookingEmailData;
import com.web.labportalbackend.common.email.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BookingOutboxEventDispatcher {

    private final ObjectMapper objectMapper;
    private final EmailService emailService;

    public void dispatch(BookingOutboxEvent event) {
        if (!BookingOutboxService.BOOKING_EMAIL_REQUESTED.equals(event.getEventType())
                || event.getEventVersion() != BookingOutboxService.CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported booking outbox event contract");
        }
        BookingEmailPayload payload = readPayload(event.getPayloadJson());
        BookingEmailData data = BookingEmailData.builder()
                .studentName(payload.studentName())
                .labName(payload.labName())
                .startTime(payload.startTime())
                .endTime(payload.endTime())
                .status(payload.status())
                .note(payload.note())
                .build();
        switch (payload.emailType()) {
            case CREATED -> emailService.sendBookingCreatedEmail(payload.recipientEmail(), data);
            case APPROVED -> emailService.sendBookingApprovedEmail(payload.recipientEmail(), data);
            case REJECTED -> emailService.sendBookingRejectedEmail(payload.recipientEmail(), data);
            case CANCELLED_BY_STUDENT ->
                    emailService.sendBookingCancelledByStudentEmail(payload.recipientEmail(), data);
        }
    }

    private BookingEmailPayload readPayload(String payloadJson) {
        try {
            return objectMapper.readValue(payloadJson, BookingEmailPayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid booking outbox payload", exception);
        }
    }
}
