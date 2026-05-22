package com.web.labportalbackend.booking.controller;

import com.web.labportalbackend.booking.dto.request.CancelBookingRequest;
import com.web.labportalbackend.booking.dto.request.CreateBookingRequest;
import com.web.labportalbackend.booking.dto.request.ReviewBookingRequest;
import com.web.labportalbackend.booking.dto.response.BookingResponse;
import com.web.labportalbackend.booking.service.BookingService;
import com.web.labportalbackend.common.dto.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Booking", description = "Lab booking management endpoints")
public class BookingController {

    private final BookingService bookingService;

    @PostMapping("/bookings")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Create booking", description = "Create a pending lab usage registration for a time slot")
    public ResponseEntity<Response<BookingResponse>> book(
            @Valid @RequestBody CreateBookingRequest request
    ) {
        BookingResponse booking = bookingService.bookCurrentUser(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(Response.ok("Booking created successfully", booking));
    }

    @GetMapping("/bookings/me")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Get my bookings", description = "Get current student's bookings")
    public ResponseEntity<Response<List<BookingResponse>>> getMyBookings() {
        return ResponseEntity.ok(Response.ok("Bookings retrieved successfully", bookingService.getMyBookings()));
    }

    @PatchMapping("/bookings/{id}/cancel")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Cancel own booking", description = "Cancel current student's booking")
    public ResponseEntity<Response<BookingResponse>> cancelBooking(
            @PathVariable Long id,
            @RequestBody(required = false) CancelBookingRequest request
    ) {
        return ResponseEntity.ok(Response.ok("Booking cancelled successfully", bookingService.cancelCurrentUserBooking(id)));
    }

    @PatchMapping("/bookings/{id}/review")
    @PreAuthorize("hasRole('LAB_MANAGER')")
    @Operation(summary = "Review booking", description = "Approve or reject a pending booking")
    public ResponseEntity<Response<BookingResponse>> reviewBooking(
            @PathVariable Long id,
            @Valid @RequestBody ReviewBookingRequest request
    ) {
        return ResponseEntity.ok(Response.ok("Booking reviewed successfully", bookingService.reviewBooking(id, request)));
    }

    @GetMapping("/slots/{slotId}/registrations")
    @PreAuthorize("hasRole('LAB_MANAGER')")
    @Operation(summary = "Get slot registrations", description = "Get bookings registered for a time slot")
    public ResponseEntity<Response<List<BookingResponse>>> getSlotRegistrations(@PathVariable Long slotId) {
        return ResponseEntity.ok(Response.ok("Slot registrations retrieved successfully", bookingService.getBookingsBySlot(slotId)));
    }
}
