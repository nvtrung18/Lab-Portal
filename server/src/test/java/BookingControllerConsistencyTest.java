package com.web.labportalbackend.booking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.labportalbackend.booking.BookingService;
import com.web.labportalbackend.common.dto.BookingResponse;
import com.web.labportalbackend.common.dto.CreateBookingRequest;
import com.web.labportalbackend.common.enums.BookingStatus;
import com.web.labportalbackend.common.exception.DuplicateBookingException;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for BookingController (Day 10).
 * Tests duplicate booking prevention and cancellation with ownership validation.
 */
@DisplayName("BookingControllerConsistency")
@WebMvcTest(BookingController.class)
class BookingControllerConsistencyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BookingService bookingService;

    private BookingResponse testBookingResponse;
    private CreateBookingRequest testRequest;

    @BeforeEach
    void setUp() {
        Instant now = Instant.now();
        testBookingResponse = BookingResponse.builder()
                .id(1L)
                .userId(1L)
                .labId(1L)
                .slotId(1L)
                .startTime(now)
                .endTime(now.plusSeconds(3600))
                .status(BookingStatus.CONFIRMED)
                .purpose("Lab research")
                .participantsCount(1)
                .createdAt(now)
                .updatedAt(now)
                .build();

        testRequest = CreateBookingRequest.builder()
                .slotId(1L)
                .purpose("Lab research")
                .participantsCount(1)
                .build();
    }

    // ===================== DUPLICATE BOOKING TESTS =====================

    @Test
    @DisplayName("POST /api/bookings should return 400 when duplicate booking attempted")
    void testCreateBooking_DuplicateBooking() throws Exception {
        // Arrange
        when(bookingService.book(anyLong(), any(CreateBookingRequest.class)))
                .thenThrow(new DuplicateBookingException(1L, 1L));

        // Act & Assert
        mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERROR"))
                .andExpect(jsonPath("$.message").containsString("User 1 has already booked slot 1"));

        verify(bookingService).book(anyLong(), any(CreateBookingRequest.class));
    }

    @Test
    @DisplayName("POST /api/bookings should show meaningful duplicate booking error")
    void testCreateBooking_DuplicateBookingErrorMessage() throws Exception {
        // Arrange
        when(bookingService.book(anyLong(), any(CreateBookingRequest.class)))
                .thenThrow(new DuplicateBookingException(5L, 10L));

        // Act & Assert
        mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").containsString("User 5"))
                .andExpect(jsonPath("$.message").containsString("slot 10"));
    }

    // ===================== CANCELLATION TESTS =====================

    @Test
    @DisplayName("PATCH /api/bookings/{id}/cancel should cancel booking successfully")
    void testCancelBooking_Success() throws Exception {
        // Arrange
        doNothing().when(bookingService).cancelBooking(1L, 1L);

        // Act & Assert
        mockMvc.perform(patch("/api/bookings/1/cancel"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("Booking cancelled successfully"));

        verify(bookingService).cancelBooking(1L, 1L);
    }

    @Test
    @DisplayName("PATCH /api/bookings/{id}/cancel should return 403 when user doesn't own booking")
    void testCancelBooking_UnauthorizedUser() throws Exception {
        // Arrange
        doThrow(new AccessDeniedException("You can only cancel your own bookings"))
                .when(bookingService).cancelBooking(1L, 1L);

        // Act & Assert
        mockMvc.perform(patch("/api/bookings/1/cancel"))
                .andDo(print())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERROR"));

        verify(bookingService).cancelBooking(1L, 1L);
    }

    @Test
    @DisplayName("PATCH /api/bookings/{id}/cancel should return 400 when already cancelled")
    void testCancelBooking_AlreadyCancelled() throws Exception {
        // Arrange
        doThrow(new IllegalStateException("Booking is already cancelled"))
                .when(bookingService).cancelBooking(1L, 1L);

        // Act & Assert
        mockMvc.perform(patch("/api/bookings/1/cancel"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").containsString("already cancelled"));

        verify(bookingService).cancelBooking(1L, 1L);
    }

    @Test
    @DisplayName("PATCH /api/bookings/{id}/cancel should return 404 when booking not found")
    void testCancelBooking_NotFound() throws Exception {
        // Arrange
        doThrow(new EntityNotFoundException("Booking not found"))
                .when(bookingService).cancelBooking(999L, 1L);

        // Act & Assert
        mockMvc.perform(patch("/api/bookings/999/cancel"))
                .andDo(print())
                .andExpect(status().isNotFound());

        verify(bookingService).cancelBooking(999L, 1L);
    }

    // ===================== INTEGRATION TESTS =====================

    @Test
    @DisplayName("Cancelled booking should not prevent user from rebooking same slot")
    void testRebookAfterCancellation() throws Exception {
        // Simulate: User cancels booking 1
        doNothing().when(bookingService).cancelBooking(1L, 1L);

        // Cancel first booking
        mockMvc.perform(patch("/api/bookings/1/cancel"))
                .andExpect(status().isOk());

        // Simulate: User can now book same slot again
        when(bookingService.book(1L, testRequest)).thenReturn(testBookingResponse);

        // Create new booking for same slot
        mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testRequest)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(1L));

        verify(bookingService).book(1L, testRequest);
    }

    @Test
    @DisplayName("Multiple requests to cancel should fail on second attempt")
    void testDoubleCancelPrevention() throws Exception {
        // Arrange
        doNothing().when(bookingService).cancelBooking(1L, 1L);
        doThrow(new IllegalStateException("Booking is already cancelled"))
                .when(bookingService).cancelBooking(1L, 1L);

        // First cancel succeeds
        mockMvc.perform(patch("/api/bookings/1/cancel"))
                .andExpect(status().isOk());

        // Setup mock to throw on second call
        doThrow(new IllegalStateException("Booking is already cancelled"))
                .when(bookingService).cancelBooking(1L, 1L);

        // Second cancel fails
        mockMvc.perform(patch("/api/bookings/1/cancel"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").containsString("already cancelled"));
    }

    // ===================== CONSTRAINT VIOLATION TESTS =====================

    @Test
    @DisplayName("POST /api/bookings should handle database constraint violations")
    void testCreateBooking_DatabaseConstraintViolation() throws Exception {
        // Arrange
        when(bookingService.book(anyLong(), any(CreateBookingRequest.class)))
                .thenThrow(new DuplicateBookingException(1L, 1L));

        // Act & Assert
        mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERROR"));
    }
}
