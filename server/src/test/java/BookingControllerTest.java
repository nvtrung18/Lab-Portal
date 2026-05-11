package com.web.labportalbackend.booking.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.labportalbackend.booking.BookingService;
import com.web.labportalbackend.common.dto.BookingResponse;
import com.web.labportalbackend.common.dto.CreateBookingRequest;
import com.web.labportalbackend.common.enums.BookingStatus;
import com.web.labportalbackend.common.exception.SlotFullException;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for BookingController.
 * Tests REST endpoints with MockMvc.
 */
@DisplayName("BookingController")
@WebMvcTest(BookingController.class)
@AutoConfigureMockMvc
class BookingControllerTest {

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

    // ===================== POST /api/bookings =====================

    @Test
    @DisplayName("POST /api/bookings should create booking successfully")
    void testCreateBooking_Success() throws Exception {
        // Arrange
        when(bookingService.book(anyLong(), any(CreateBookingRequest.class)))
                .thenReturn(testBookingResponse);

        // Act & Assert
        mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testRequest)))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("Booking created successfully"))
                .andExpect(jsonPath("$.data.id").value(1L))
                .andExpect(jsonPath("$.data.user_id").value(1L))
                .andExpect(jsonPath("$.data.slot_id").value(1L))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        verify(bookingService).book(anyLong(), any(CreateBookingRequest.class));
    }

    @Test
    @DisplayName("POST /api/bookings should return 400 when slot is full")
    void testCreateBooking_SlotFull() throws Exception {
        // Arrange
        when(bookingService.book(anyLong(), any(CreateBookingRequest.class)))
                .thenThrow(new SlotFullException(1L, 3, 3));

        // Act & Assert
        mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERROR"))
                .andExpect(jsonPath("$.message").exists());

        verify(bookingService).book(anyLong(), any(CreateBookingRequest.class));
    }

    @Test
    @DisplayName("POST /api/bookings should return 400 when slot not found")
    void testCreateBooking_SlotNotFound() throws Exception {
        // Arrange
        when(bookingService.book(anyLong(), any(CreateBookingRequest.class)))
                .thenThrow(new EntityNotFoundException("Time slot not found"));

        // Act & Assert
        mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(testRequest)))
                .andDo(print())
                .andExpect(status().isNotFound());

        verify(bookingService).book(anyLong(), any(CreateBookingRequest.class));
    }

    @Test
    @DisplayName("POST /api/bookings should return 400 when slot_id is null")
    void testCreateBooking_MissingSlotId() throws Exception {
        // Arrange
        CreateBookingRequest invalidRequest = CreateBookingRequest.builder()
                .slotId(null)
                .build();

        // Act & Assert
        mockMvc.perform(post("/api/bookings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    // ===================== GET /api/bookings =====================

    @Test
    @DisplayName("GET /api/bookings should return paginated list of bookings")
    void testGetAllBookings_Success() throws Exception {
        // Arrange
        Page<BookingResponse> page = new PageImpl<>(List.of(testBookingResponse), PageRequest.of(0, 10), 1);
        when(bookingService.getAllBookings(any())).thenReturn(page);

        // Act & Assert
        mockMvc.perform(get("/api/bookings")
                .param("page", "0")
                .param("size", "10"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("Bookings retrieved successfully"))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].id").value(1L))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        verify(bookingService).getAllBookings(any());
    }

    @Test
    @DisplayName("GET /api/bookings should return empty list when no bookings exist")
    void testGetAllBookings_Empty() throws Exception {
        // Arrange
        Page<BookingResponse> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 10), 0);
        when(bookingService.getAllBookings(any())).thenReturn(emptyPage);

        // Act & Assert
        mockMvc.perform(get("/api/bookings"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(0)))
                .andExpect(jsonPath("$.data.totalElements").value(0));

        verify(bookingService).getAllBookings(any());
    }

    // ===================== GET /api/bookings/user/{userId} =====================

    @Test
    @DisplayName("GET /api/bookings/user/{userId} should return user bookings")
    void testGetBookingsByUser_Success() throws Exception {
        // Arrange
        when(bookingService.getBookingsByUser(1L))
                .thenReturn(List.of(testBookingResponse));

        // Act & Assert
        mockMvc.perform(get("/api/bookings/user/1"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("User bookings retrieved successfully"))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].user_id").value(1L));

        verify(bookingService).getBookingsByUser(1L);
    }

    @Test
    @DisplayName("GET /api/bookings/user/{userId} should return 404 when user not found")
    void testGetBookingsByUser_NotFound() throws Exception {
        // Arrange
        when(bookingService.getBookingsByUser(999L))
                .thenThrow(new EntityNotFoundException("User not found"));

        // Act & Assert
        mockMvc.perform(get("/api/bookings/user/999"))
                .andDo(print())
                .andExpect(status().isNotFound());

        verify(bookingService).getBookingsByUser(999L);
    }

    // ===================== GET /api/bookings/slot/{slotId} =====================

    @Test
    @DisplayName("GET /api/bookings/slot/{slotId} should return slot bookings")
    void testGetBookingsBySlot_Success() throws Exception {
        // Arrange
        when(bookingService.getBookingsBySlot(1L))
                .thenReturn(List.of(testBookingResponse));

        // Act & Assert
        mockMvc.perform(get("/api/bookings/slot/1"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("Slot bookings retrieved successfully"))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].slot_id").value(1L));

        verify(bookingService).getBookingsBySlot(1L);
    }

    @Test
    @DisplayName("GET /api/bookings/slot/{slotId} should return 404 when slot not found")
    void testGetBookingsBySlot_NotFound() throws Exception {
        // Arrange
        when(bookingService.getBookingsBySlot(999L))
                .thenThrow(new EntityNotFoundException("Time slot not found"));

        // Act & Assert
        mockMvc.perform(get("/api/bookings/slot/999"))
                .andDo(print())
                .andExpect(status().isNotFound());

        verify(bookingService).getBookingsBySlot(999L);
    }

    // ===================== PATCH /api/bookings/{id}/cancel =====================

    @Test
    @DisplayName("PATCH /api/bookings/{id}/cancel should cancel booking successfully")
    void testCancelBooking_Success() throws Exception {
        // Arrange
        doNothing().when(bookingService).cancelBooking(1L);

        // Act & Assert
        mockMvc.perform(patch("/api/bookings/1/cancel"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("OK"))
                .andExpect(jsonPath("$.message").value("Booking cancelled successfully"));

        verify(bookingService).cancelBooking(1L);
    }

    @Test
    @DisplayName("PATCH /api/bookings/{id}/cancel should return 404 when booking not found")
    void testCancelBooking_NotFound() throws Exception {
        // Arrange
        doThrow(new EntityNotFoundException("Booking not found"))
                .when(bookingService).cancelBooking(999L);

        // Act & Assert
        mockMvc.perform(patch("/api/bookings/999/cancel"))
                .andDo(print())
                .andExpect(status().isNotFound());

        verify(bookingService).cancelBooking(999L);
    }

    // ===================== GET /api/bookings/{id} =====================

    @Test
    @DisplayName("GET /api/bookings/{id} should return booking by id (placeholder)")
    void testGetBookingById() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/bookings/1"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").contains("pending"));
    }
}
