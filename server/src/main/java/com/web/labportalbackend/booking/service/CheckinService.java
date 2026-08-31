package com.web.labportalbackend.booking.service;

import com.web.labportalbackend.booking.dto.response.BookingResponse;
import com.web.labportalbackend.booking.dto.response.CheckinQrResponse;
import com.web.labportalbackend.face.enums.FaceFallbackReason;

public interface CheckinService {
    CheckinQrResponse createQrForCurrentStudent(Long bookingId, FaceFallbackReason fallbackReason);

    BookingResponse confirmByCurrentManager(String token);

    BookingResponse manualCheckinByCurrentManager(Long bookingId, String reason);
}
