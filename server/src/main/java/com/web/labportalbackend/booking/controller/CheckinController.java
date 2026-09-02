package com.web.labportalbackend.booking.controller;

import com.web.labportalbackend.booking.dto.request.CheckinRequest;
import com.web.labportalbackend.booking.dto.request.CreateCheckinQrRequest;
import com.web.labportalbackend.booking.dto.request.ManualCheckinRequest;
import com.web.labportalbackend.booking.dto.request.ReviewCheckinQrRequest;
import com.web.labportalbackend.booking.dto.response.BookingResponse;
import com.web.labportalbackend.booking.dto.response.CheckinQrResponse;
import com.web.labportalbackend.booking.dto.response.CheckinResponse;
import com.web.labportalbackend.booking.dto.response.CheckinQrRequestResponse;
import com.web.labportalbackend.booking.service.CheckinService;
import com.web.labportalbackend.common.dto.Response;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CheckinController {

    private final CheckinService checkinService;

    @PostMapping("/checkin/qr-requests")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Request a check-in QR", description = "Submit a short-lived QR fallback request to the manager of the booking's lab")
    public ResponseEntity<Response<CheckinQrResponse>> requestQr(@Valid @RequestBody CreateCheckinQrRequest request) {
        CheckinQrResponse result = checkinService.requestQrForCurrentStudent(
                request.getBookingId(), request.getFallbackReason(), request.getCustomReason());
        return ResponseEntity.ok(Response.ok(result.getMessage(), result));
    }

    @GetMapping("/checkin/qr-requests/me")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Get current student's QR request for a booking")
    public ResponseEntity<Response<CheckinQrResponse>> getMyQrRequest(@RequestParam Long bookingId) {
        CheckinQrResponse result = checkinService.getCurrentStudentQrRequest(bookingId);
        return ResponseEntity.ok(Response.ok(result.getMessage(), result));
    }

    @GetMapping("/checkin/qr-requests/pending")
    @PreAuthorize("hasRole('LAB_MANAGER')")
    @Operation(summary = "List pending QR requests for the current manager's lab")
    public ResponseEntity<Response<List<CheckinQrRequestResponse>>> getPendingQrRequests() {
        return ResponseEntity.ok(Response.ok(checkinService.getPendingQrRequestsForCurrentManager()));
    }

    @PatchMapping("/checkin/qr-requests/{requestId}/review")
    @PreAuthorize("hasRole('LAB_MANAGER')")
    @Operation(summary = "Approve or reject a QR request for the current manager's lab")
    public ResponseEntity<Response<CheckinQrRequestResponse>> reviewQrRequest(
            @PathVariable String requestId,
            @Valid @RequestBody ReviewCheckinQrRequest request) {
        return ResponseEntity.ok(Response.ok("Đã xử lý yêu cầu QR.",
                checkinService.reviewQrRequestByCurrentManager(requestId, request.approved())));
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
