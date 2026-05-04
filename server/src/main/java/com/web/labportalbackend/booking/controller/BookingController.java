package com.web.labportalbackend.booking.controller;

import com.web.labportalbackend.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Booking management endpoints skeleton.
 * Service layer will be connected in Day 3.
 */
@RestController
@RequestMapping("/bookings")
@Tag(name = "Booking", description = "Lab booking management endpoints")
public class BookingController {

    @GetMapping("/health")
    @Operation(summary = "Booking service health check")
    public ResponseEntity<ApiResponse<String>> health() {
        return ResponseEntity.ok(ApiResponse.success("Booking service is healthy", "UP"));
    }

    @GetMapping
    @Operation(summary = "Get all bookings")
    public ResponseEntity<ApiResponse<String>> getAllBookings() {
        return ResponseEntity.ok(ApiResponse.success("Get all bookings — service pending"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get booking by ID")
    public ResponseEntity<ApiResponse<String>> getBookingById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Get booking by ID — service pending", "ID: " + id));
    }

    @PostMapping
    @Operation(summary = "Create booking", description = "Create a new lab booking")
    public ResponseEntity<ApiResponse<String>> createBooking() {
        return ResponseEntity.ok(ApiResponse.success("Create booking — service pending"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update booking")
    public ResponseEntity<ApiResponse<String>> updateBooking(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Update booking — service pending", "ID: " + id));
    }

    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Cancel booking", description = "Cancel an existing booking")
    public ResponseEntity<ApiResponse<String>> cancelBooking(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Cancel booking — service pending", "ID: " + id));
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "Get bookings by user", description = "Get all bookings for a specific user")
    public ResponseEntity<ApiResponse<String>> getBookingsByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success("Get user bookings — service pending", "User ID: " + userId));
    }

    @GetMapping("/lab/{labId}")
    @Operation(summary = "Get bookings by lab", description = "Get all bookings for a specific lab")
    public ResponseEntity<ApiResponse<String>> getBookingsByLab(@PathVariable Long labId) {
        return ResponseEntity.ok(ApiResponse.success("Get lab bookings — service pending", "Lab ID: " + labId));
    }
}
