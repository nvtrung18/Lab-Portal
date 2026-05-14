package com.web.labportalbackend.booking.controller;

import com.web.labportalbackend.booking.service.BookingService;
import com.web.labportalbackend.booking.service.WaitlistService;
import com.web.labportalbackend.booking.dto.response.BookingResponse;
import com.web.labportalbackend.booking.dto.response.WaitlistResponse;
import com.web.labportalbackend.booking.dto.request.CreateBookingRequest;
import com.web.labportalbackend.common.dto.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for booking management.
 * Handles booking creation, listing, and cancellation.
 */
@Slf4j
@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
@Tag(name = "Booking", description = "Lab booking management endpoints")
public class BookingController {

    private final BookingService bookingService;
    private final WaitlistService waitlistService;

    /**
     * Create a new booking for a time slot.
     *
     * @param request the booking request containing slot_id
     * @return the created booking wrapped in Response
     */
    @PostMapping
    @Operation(summary = "Create booking", description = "Create a new lab booking for a time slot")
    public ResponseEntity<Response<BookingResponse>> book(
            @Valid @RequestBody CreateBookingRequest request
    ) {
        // In a real scenario, userId would come from Security Context
        // For now, we use a default value (this should be replaced with actual user extraction)
        Long userId = 1L;  // TODO: Extract from Security Context
        
        log.info("Creating booking for user: {}, slot: {}", userId, request.getSlotId());
        BookingResponse booking = bookingService.book(userId, request);
        
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(Response.ok("Booking created successfully", booking));
    }

    /**
     * Get all bookings with pagination.
     *
     * @param pageable pagination parameters
     * @return paginated list of bookings wrapped in Response
     */
    @GetMapping
    @Operation(summary = "Get all bookings", description = "Retrieve all bookings with pagination")
    public ResponseEntity<Response<Page<BookingResponse>>> getAllBookings(
            Pageable pageable
    ) {
        log.info("Fetching all bookings. Page: {}, Size: {}", pageable.getPageNumber(), pageable.getPageSize());
        Page<BookingResponse> bookings = bookingService.getAllBookings(pageable);
        
        return ResponseEntity.ok(
                Response.ok("Bookings retrieved successfully", bookings)
        );
    }

    /**
     * Get bookings for a specific user.
     *
     * @param userId the user ID
     * @return list of bookings wrapped in Response
     */
    @GetMapping("/user/{userId}")
    @Operation(summary = "Get user bookings", description = "Get all bookings for a specific user")
    public ResponseEntity<Response<List<BookingResponse>>> getBookingsByUser(
            @PathVariable Long userId
    ) {
        log.info("Fetching bookings for user: {}", userId);
        List<BookingResponse> bookings = bookingService.getBookingsByUser(userId);
        
        return ResponseEntity.ok(
                Response.ok("User bookings retrieved successfully", bookings)
        );
    }

    /**
     * Get bookings for a specific time slot.
     *
     * @param slotId the time slot ID
     * @return list of bookings wrapped in Response
     */
    @GetMapping("/slot/{slotId}")
    @Operation(summary = "Get slot bookings", description = "Get all bookings for a specific time slot")
    public ResponseEntity<Response<List<BookingResponse>>> getBookingsBySlot(
            @PathVariable Long slotId
    ) {
        log.info("Fetching bookings for slot: {}", slotId);
        List<BookingResponse> bookings = bookingService.getBookingsBySlot(slotId);
        
        return ResponseEntity.ok(
                Response.ok("Slot bookings retrieved successfully", bookings)
        );
    }

    /**
     * Get a specific booking by ID.
     *
     * @param id the booking ID
     * @return the booking wrapped in Response
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get booking by ID")
    public ResponseEntity<Response<BookingResponse>> getBookingById(@PathVariable Long id) {
        log.info("Fetching booking with ID: {}", id);
        // TODO: Implement in service layer
        return ResponseEntity.ok(Response.ok("Get booking by ID — service pending", null));
    }

    /**
     * Cancel an existing booking.
     * Only the booking owner can cancel their own booking.
     *
     * @param id the booking ID
     * @return success message wrapped in Response
     */
    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Cancel booking", description = "Cancel an existing booking (ownership validated)")
    public ResponseEntity<Response<Void>> cancelBooking(@PathVariable Long id) {
        // In a real scenario, userId would come from Security Context
        Long userId = 1L;  // TODO: Extract from Security Context
        
        log.info("Cancelling booking {} for user {}", id, userId);
        bookingService.cancelBooking(id, userId);
        
        return ResponseEntity.ok(
                Response.ok("Booking cancelled successfully")
        );
    }

    /**
     * Get waitlist for a specific time slot.
     * Returns all users waiting for the slot, ordered by position (lowest first).
     *
     * @param slotId the time slot ID
     * @return ordered list of waitlist entries wrapped in Response
     */
    @GetMapping("/slots/{slotId}/waitlist")
    @Operation(summary = "Get slot waitlist", description = "Get all waitlist entries for a specific time slot, ordered by position")
    public ResponseEntity<Response<List<WaitlistResponse>>> getSlotWaitlist(
            @PathVariable Long slotId
    ) {
        log.info("Fetching waitlist for slot: {}", slotId);
        List<WaitlistResponse> waitlist = waitlistService.getWaitlistBySlot(slotId);
        
        return ResponseEntity.ok(
                Response.ok("Slot waitlist retrieved successfully", waitlist)
        );
    }
}

