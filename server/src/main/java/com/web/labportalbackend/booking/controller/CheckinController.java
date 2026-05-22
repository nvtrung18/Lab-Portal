package com.web.labportalbackend.booking.controller;

import com.web.labportalbackend.booking.dto.request.CheckInRequest;
import com.web.labportalbackend.booking.dto.response.BookingResponse;
import com.web.labportalbackend.booking.dto.response.CheckInResponse;
import com.web.labportalbackend.booking.service.CheckInService;
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
public class CheckInController {

    private final CheckInService checkInService;

    @PostMapping("/checkin")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Check in by QR token", description = "Confirm attendance for an approved booking")
    public ResponseEntity<Response<CheckInResponse>> checkIn(@Valid @RequestBody CheckInRequest request) {
        BookingResponse booking = checkInService.checkInCurrentUser(request.getToken());
        return ResponseEntity.ok(Response.ok(
                "Check-in thành công.",
                CheckInResponse.builder().booking(booking).build()
        ));
    }
}
