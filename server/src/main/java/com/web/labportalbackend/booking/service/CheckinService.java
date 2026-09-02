package com.web.labportalbackend.booking.service;

import com.web.labportalbackend.booking.dto.response.BookingResponse;
import com.web.labportalbackend.booking.dto.response.CheckinQrResponse;
import com.web.labportalbackend.booking.dto.response.CheckinQrRequestResponse;
import com.web.labportalbackend.booking.dto.response.CheckinQrHistoryResponse;
import com.web.labportalbackend.face.enums.FaceFallbackReason;

import java.util.List;

public interface CheckinService {
    CheckinQrResponse requestQrForCurrentStudent(Long bookingId, FaceFallbackReason fallbackReason, String customReason);

    CheckinQrResponse getCurrentStudentQrRequest(Long bookingId);

    List<CheckinQrHistoryResponse> getCurrentStudentQrHistory();

    List<CheckinQrRequestResponse> getPendingQrRequestsForCurrentManager();

    CheckinQrRequestResponse reviewQrRequestByCurrentManager(String requestId, boolean approved);

    BookingResponse confirmByCurrentManager(String token);

    BookingResponse manualCheckinByCurrentManager(Long bookingId, String reason);
}
