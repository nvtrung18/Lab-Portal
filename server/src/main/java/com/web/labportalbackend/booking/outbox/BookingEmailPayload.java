package com.web.labportalbackend.booking.outbox;

import com.web.labportalbackend.booking.event.BookingEmailType;

import java.time.Instant;

public record BookingEmailPayload(
        String recipientEmail,
        BookingEmailType emailType,
        String studentName,
        String labName,
        Instant startTime,
        Instant endTime,
        String status,
        String note
) {
}
