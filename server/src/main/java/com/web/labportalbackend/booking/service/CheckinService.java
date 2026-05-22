package com.web.labportalbackend.booking.service;

import com.web.labportalbackend.booking.dto.response.BookingResponse;
import com.web.labportalbackend.booking.dto.response.CheckinQrResponse;

public interface CheckinService {
    CheckinQrResponse createQrForCurrentStudent(Long bookingId);

    BookingResponse confirmByCurrentManager(String token);
}
