package com.web.labportalbackend.booking.controller;

import com.web.labportalbackend.booking.dto.request.CheckinRequest;
import com.web.labportalbackend.booking.dto.request.CreateCheckinQrRequest;
import com.web.labportalbackend.booking.dto.request.ManualCheckinRequest;
import com.web.labportalbackend.booking.dto.response.BookingResponse;
import com.web.labportalbackend.booking.dto.response.CheckinQrResponse;
import com.web.labportalbackend.booking.dto.response.CheckinResponse;
import com.web.labportalbackend.booking.service.CheckinService;
import com.web.labportalbackend.common.dto.Response;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CheckinController {

    private final CheckinService checkinService;

    @PostMapping("/checkin/qr")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Create check-in QR token", description = "Create a short-lived QR token for an approved booking")
    public ResponseEntity<Response<CheckinQrResponse>> createQr(@Valid @RequestBody CreateCheckinQrRequest request) {
        CheckinQrResponse result = checkinService.createQrForCurrentStudent(
                request.getBookingId(), request.getFallbackReason());
        return ResponseEntity.ok(Response.ok(result.getMessage(), result));
    }

    @PostMapping("/checkin/confirm")
    @PreAuthorize("hasRole('LAB_MANAGER')")
    @Operation(summary = "Confirm check-in by QR token", description = "Lab manager confirms student attendance by QR token")
    public ResponseEntity<Response<CheckinResponse>> confirm(@Valid @RequestBody CheckinRequest request) {
        BookingResponse booking = checkinService.confirmByCurrentManager(request.getToken());
        return ResponseEntity.ok(Response.ok(
                "Xác nhận có mặt thành công.",
                CheckinResponse.builder().booking(booking).build()
        ));
    }

    @PostMapping("/checkin/manual")
    @PreAuthorize("hasRole('LAB_MANAGER')")
    @Operation(summary = "Check in manually", description = "Lab manager performs an audited manual fallback check-in")
    public ResponseEntity<Response<CheckinResponse>> manual(@Valid @RequestBody ManualCheckinRequest request) {
        BookingResponse booking = checkinService.manualCheckinByCurrentManager(request.bookingId(), request.reason());
        return ResponseEntity.ok(Response.ok(
                "Xác nhận có mặt thủ công thành công.",
                CheckinResponse.builder().booking(booking).build()
        ));
    }
}
