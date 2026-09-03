package com.web.labportalbackend.booking.event;

import com.web.labportalbackend.common.email.BookingEmailData;

public record BookingEmailRequestedEvent(
        Long bookingId,
        BookingEmailType type,
        String recipientEmail,
        BookingEmailData data
) {
}
