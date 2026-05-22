package com.web.labportalbackend.booking.service;

import com.web.labportalbackend.booking.dto.response.BookingResponse;

public interface CheckInService {
    BookingResponse checkInCurrentUser(String token);

    int sendDueCheckInEmails();
}
