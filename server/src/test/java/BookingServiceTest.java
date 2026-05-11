package com.web.labportalbackend.booking.service;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.booking.BookingService;
import com.web.labportalbackend.booking.BookingServiceImpl;
import com.web.labportalbackend.booking.entity.Booking;
import com.web.labportalbackend.booking.entity.TimeSlot;
import com.web.labportalbackend.booking.repository.BookingRepository;
import com.web.labportalbackend.booking.repository.TimeSlotRepository;
import com.web.labportalbackend.common.dto.BookingResponse;
import com.web.labportalbackend.common.dto.CreateBookingRequest;
import com.web.labportalbackend.common.enums.BookingStatus;
import com.web.labportalbackend.common.enums.TimeSlotStatus;
import com.web.labportalbackend.common.exception.SlotFullException;
import com.web.labportalbackend.laboratory.entity.Lab;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Unit tests for BookingService.
 * Focuses on capacity validation, booking creation, and data retrieval.
 */
@DisplayName("BookingService")
@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private TimeSlotRepository timeSlotRepository;

    @Mock
    private UserRepository userRepository;

    private BookingService bookingService;
    private User testUser;
    private Lab testLab;
    private TimeSlot testTimeSlot;
    private Booking testBooking;

    @BeforeEach
    void setUp() {
        bookingService = new BookingServiceImpl(bookingRepository, timeSlotRepository, userRepository);

        // Setup test data
        testUser = User.builder()
                .id(1L)
                .email("user@test.com")
                .username("testuser")
                .firstName("Test")
                .lastName("User")
                .build();

        testLab = Lab.builder()
                .id(1L)
                .name("Lab A")
                .description("Test Lab")
                .capacity(20)
                .build();

        Instant now = Instant.now();
        testTimeSlot = TimeSlot.builder()
                .id(1L)
                .lab(testLab)
                .startTime(now)
                .endTime(now.plusSeconds(3600))
                .capacity(3)
                .status(TimeSlotStatus.AVAILABLE)
                .build();

        testBooking = Booking.builder()
                .id(1L)
                .user(testUser)
                .lab(testLab)
                .timeSlot(testTimeSlot)
                .startTime(testTimeSlot.getStartTime())
                .endTime(testTimeSlot.getEndTime())
                .status(BookingStatus.CONFIRMED)
                .participantsCount(1)
                .build();
    }

    // ===================== SUCCESS CASES =====================

    @Test
    @DisplayName("book() should successfully create booking when slot has available capacity")
    void testBook_Success_WhenCapacityAvailable() {
        // Arrange
        CreateBookingRequest request = CreateBookingRequest.builder()
                .slotId(1L)
                .purpose("Lab research")
                .participantsCount(1)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(timeSlotRepository.findById(1L)).thenReturn(Optional.of(testTimeSlot));
        when(bookingRepository.countByTimeSlotId(1L)).thenReturn(1L);  // 1 booking, capacity is 3
        when(bookingRepository.save(any(Booking.class))).thenReturn(testBooking);

        // Act
        BookingResponse response = bookingService.book(1L, request);

        // Assert
        assertThat(response)
                .isNotNull()
                .satisfies(r -> {
                    assertThat(r.getId()).isEqualTo(1L);
                    assertThat(r.getUserId()).isEqualTo(1L);
                    assertThat(r.getLabId()).isEqualTo(1L);
                    assertThat(r.getSlotId()).isEqualTo(1L);
                    assertThat(r.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
                });

        verify(bookingRepository).save(any(Booking.class));
        verify(userRepository).findById(1L);
        verify(timeSlotRepository).findById(1L);
        verify(bookingRepository).countByTimeSlotId(1L);
    }

    @Test
    @DisplayName("book() should successfully create booking when slot is at max capacity - 1")
    void testBook_Success_WhenAtMaxCapacityMinusOne() {
        // Arrange
        CreateBookingRequest request = CreateBookingRequest.builder()
                .slotId(1L)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(timeSlotRepository.findById(1L)).thenReturn(Optional.of(testTimeSlot));
        when(bookingRepository.countByTimeSlotId(1L)).thenReturn(2L);  // 2 bookings, capacity is 3
        when(bookingRepository.save(any(Booking.class))).thenReturn(testBooking);

        // Act
        BookingResponse response = bookingService.book(1L, request);

        // Assert
        assertThat(response).isNotNull();
        verify(bookingRepository).save(any(Booking.class));
    }

    @Test
    @DisplayName("getAllBookings() should return paginated list of bookings")
    void testGetAllBookings_Success() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<Booking> bookingPage = new PageImpl<>(List.of(testBooking), pageable, 1);

        when(bookingRepository.findAll(pageable)).thenReturn(bookingPage);

        // Act
        Page<BookingResponse> response = bookingService.getAllBookings(pageable);

        // Assert
        assertThat(response)
                .isNotNull()
                .isNotEmpty()
                .hasSize(1);

        assertThat(response.getContent().get(0))
                .satisfies(booking -> {
                    assertThat(booking.getId()).isEqualTo(1L);
                    assertThat(booking.getUserId()).isEqualTo(1L);
                });

        verify(bookingRepository).findAll(pageable);
    }

    @Test
    @DisplayName("getBookingsByUser() should return list of bookings for user")
    void testGetBookingsByUser_Success() {
        // Arrange
        when(userRepository.existsById(1L)).thenReturn(true);
        when(bookingRepository.findByUserId(1L)).thenReturn(List.of(testBooking));

        // Act
        List<BookingResponse> response = bookingService.getBookingsByUser(1L);

        // Assert
        assertThat(response)
                .isNotNull()
                .isNotEmpty()
                .hasSize(1);

        assertThat(response.get(0))
                .satisfies(booking -> {
                    assertThat(booking.getId()).isEqualTo(1L);
                    assertThat(booking.getUserId()).isEqualTo(1L);
                });

        verify(userRepository).existsById(1L);
        verify(bookingRepository).findByUserId(1L);
    }

    @Test
    @DisplayName("getBookingsBySlot() should return list of bookings for slot")
    void testGetBookingsBySlot_Success() {
        // Arrange
        when(timeSlotRepository.existsById(1L)).thenReturn(true);
        when(bookingRepository.findBySlotId(1L)).thenReturn(List.of(testBooking));

        // Act
        List<BookingResponse> response = bookingService.getBookingsBySlot(1L);

        // Assert
        assertThat(response)
                .isNotNull()
                .isNotEmpty()
                .hasSize(1);

        assertThat(response.get(0)).satisfies(booking ->
                assertThat(booking.getSlotId()).isEqualTo(1L)
        );

        verify(timeSlotRepository).existsById(1L);
        verify(bookingRepository).findBySlotId(1L);
    }

    @Test
    @DisplayName("cancelBooking() should mark booking as CANCELLED")
    void testCancelBooking_Success() {
        // Arrange
        Booking bookingToCancel = Booking.builder()
                .id(1L)
                .status(BookingStatus.CONFIRMED)
                .build();

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(bookingToCancel));
        when(bookingRepository.save(any(Booking.class))).thenReturn(bookingToCancel);

        // Act
        bookingService.cancelBooking(1L);

        // Assert
        assertThat(bookingToCancel.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(bookingRepository).findById(1L);
        verify(bookingRepository).save(any(Booking.class));
    }

    // ===================== FAILURE CASES =====================

    @Test
    @DisplayName("book() should throw SlotFullException when slot is at max capacity")
    void testBook_Failure_WhenSlotAtMaxCapacity() {
        // Arrange
        CreateBookingRequest request = CreateBookingRequest.builder()
                .slotId(1L)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(timeSlotRepository.findById(1L)).thenReturn(Optional.of(testTimeSlot));
        when(bookingRepository.countByTimeSlotId(1L)).thenReturn(3L);  // 3 bookings, capacity is 3

        // Act & Assert
        assertThatThrownBy(() -> bookingService.book(1L, request))
                .isInstanceOf(SlotFullException.class)
                .hasMessageContaining("Slot 1 is full")
                .hasMessageContaining("capacity: 3");

        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    @DisplayName("book() should throw SlotFullException when slot exceeds max capacity")
    void testBook_Failure_WhenSlotExceedsMaxCapacity() {
        // Arrange
        CreateBookingRequest request = CreateBookingRequest.builder()
                .slotId(1L)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(timeSlotRepository.findById(1L)).thenReturn(Optional.of(testTimeSlot));
        when(bookingRepository.countByTimeSlotId(1L)).thenReturn(5L);  // 5 bookings, capacity is 3

        // Act & Assert
        assertThatThrownBy(() -> bookingService.book(1L, request))
                .isInstanceOf(SlotFullException.class);

        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    @DisplayName("book() should throw EntityNotFoundException when user not found")
    void testBook_Failure_WhenUserNotFound() {
        // Arrange
        CreateBookingRequest request = CreateBookingRequest.builder()
                .slotId(1L)
                .build();

        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> bookingService.book(999L, request))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("User not found");

        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    @DisplayName("book() should throw EntityNotFoundException when time slot not found")
    void testBook_Failure_WhenTimeSlotNotFound() {
        // Arrange
        CreateBookingRequest request = CreateBookingRequest.builder()
                .slotId(999L)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(timeSlotRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> bookingService.book(1L, request))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Time slot not found");

        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    @DisplayName("getBookingsByUser() should throw EntityNotFoundException when user not found")
    void testGetBookingsByUser_Failure_WhenUserNotFound() {
        // Arrange
        when(userRepository.existsById(999L)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> bookingService.getBookingsByUser(999L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("User not found");

        verify(bookingRepository, never()).findByUserId(anyLong());
    }

    @Test
    @DisplayName("getBookingsBySlot() should throw EntityNotFoundException when slot not found")
    void testGetBookingsBySlot_Failure_WhenSlotNotFound() {
        // Arrange
        when(timeSlotRepository.existsById(999L)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> bookingService.getBookingsBySlot(999L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Time slot not found");

        verify(bookingRepository, never()).findBySlotId(anyLong());
    }

    @Test
    @DisplayName("cancelBooking() should throw EntityNotFoundException when booking not found")
    void testCancelBooking_Failure_WhenBookingNotFound() {
        // Arrange
        when(bookingRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> bookingService.cancelBooking(999L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Booking not found");

        verify(bookingRepository, never()).save(any(Booking.class));
    }

    // ===================== EDGE CASES =====================

    @Test
    @DisplayName("getAllBookings() should return empty page when no bookings exist")
    void testGetAllBookings_EmptyPage() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<Booking> emptyPage = new PageImpl<>(List.of(), pageable, 0);

        when(bookingRepository.findAll(pageable)).thenReturn(emptyPage);

        // Act
        Page<BookingResponse> response = bookingService.getAllBookings(pageable);

        // Assert
        assertThat(response)
                .isNotNull()
                .isEmpty()
                .hasSize(0);

        verify(bookingRepository).findAll(pageable);
    }

    @Test
    @DisplayName("getBookingsByUser() should return empty list when user has no bookings")
    void testGetBookingsByUser_EmptyList() {
        // Arrange
        when(userRepository.existsById(1L)).thenReturn(true);
        when(bookingRepository.findByUserId(1L)).thenReturn(List.of());

        // Act
        List<BookingResponse> response = bookingService.getBookingsByUser(1L);

        // Assert
        assertThat(response)
                .isNotNull()
                .isEmpty();

        verify(bookingRepository).findByUserId(1L);
    }

    @Test
    @DisplayName("book() should save booking with correct user and time slot references")
    void testBook_VerifyBookingReferences() {
        // Arrange
        CreateBookingRequest request = CreateBookingRequest.builder()
                .slotId(1L)
                .purpose("Research")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(timeSlotRepository.findById(1L)).thenReturn(Optional.of(testTimeSlot));
        when(bookingRepository.countByTimeSlotId(1L)).thenReturn(0L);
        when(bookingRepository.save(any(Booking.class))).thenReturn(testBooking);

        // Act
        bookingService.book(1L, request);

        // Assert
        verify(bookingRepository).save(argThat(booking ->
                booking.getUser().getId().equals(1L) &&
                        booking.getTimeSlot().getId().equals(1L) &&
                        booking.getLab().getId().equals(1L) &&
                        booking.getStatus().equals(BookingStatus.CONFIRMED)
        ));
    }
}
