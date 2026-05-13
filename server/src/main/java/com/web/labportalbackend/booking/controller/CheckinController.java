package com.web.labportalbackend.booking.controller;

import com.web.labportalbackend.booking.dto.request.CheckinRequest;
import com.web.labportalbackend.booking.dto.response.CheckinResponse;
import com.web.labportalbackend.booking.service.CheckinService;
import com.web.labportalbackend.common.dto.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for check-in operations.
 * Handles user check-in to confirmed lab bookings.
 */
@Slf4j
@RestController
@RequestMapping("/api/checkin")
@RequiredArgsConstructor
@Tag(name = "Check-in", description = "Lab check-in management endpoints")
public class CheckinController {

    private final CheckinService checkinService;

    /**
     * Check in to a confirmed booking.
     * 
     * Time window: startTime - 15 minutes to endTime
     * Only CONFIRMED bookings can be checked in.
     * 
     * @param request the check-in request containing booking_id
     * @return the check-in confirmation wrapped in Response
     */
    @PostMapping
    @Operation(summary = "Check in to booking", description = "Check in to a confirmed lab booking within the allowed time window")
    public ResponseEntity<Response<CheckinResponse>> checkIn(
            @Valid @RequestBody CheckinRequest request
    ) {
        // TODO: Extract from Security Context instead of hardcoding
        Long userId = 1L;

        log.info("Check-in request for booking: {}, user: {}", request.getBookingId(), userId);
        CheckinResponse checkin = checkinService.checkIn(request.getBookingId(), userId);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(Response.ok("Check-in successful", checkin));
    }
}
