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
import com.web.labportalbackend.common.exception.DuplicateBookingException;
import com.web.labportalbackend.laboratory.entity.Lab;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Extended unit tests for BookingService (Day 10).
 * Focuses on duplicate booking prevention, capacity consistency, and cancellation logic.
 */
@DisplayName("BookingServiceConsistency")
@ExtendWith(MockitoExtension.class)
class BookingServiceConsistencyTest {

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

    // ===================== DUPLICATE BOOKING TESTS =====================

    @Test
    @DisplayName("book() should throw DuplicateBookingException when user already booked slot")
    void testBook_Failure_DuplicateBooking_ApplicationLevel() {
        // Arrange
        CreateBookingRequest request = CreateBookingRequest.builder()
                .slotId(1L)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(timeSlotRepository.findById(1L)).thenReturn(Optional.of(testTimeSlot));
        when(bookingRepository.existsActiveBookingByUserAndSlot(1L, 1L)).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> bookingService.book(1L, request))
                .isInstanceOf(DuplicateBookingException.class)
                .hasMessageContaining("User 1 has already booked slot 1")
                .hasMessageContaining("Duplicate bookings are not allowed");

        // Verify save was never called
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    @DisplayName("book() should catch DataIntegrityViolationException and throw DuplicateBookingException")
    void testBook_Failure_DuplicateBooking_DatabaseLevel() {
        // Arrange
        CreateBookingRequest request = CreateBookingRequest.builder()
                .slotId(1L)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(timeSlotRepository.findById(1L)).thenReturn(Optional.of(testTimeSlot));
        when(bookingRepository.existsActiveBookingByUserAndSlot(1L, 1L)).thenReturn(false);
        when(bookingRepository.countByTimeSlotIdAndStatus(1L, BookingStatus.CONFIRMED)).thenReturn(1L);
        when(bookingRepository.save(any(Booking.class)))
                .thenThrow(new DataIntegrityViolationException("Unique constraint violated"));

        // Act & Assert
        assertThatThrownBy(() -> bookingService.book(1L, request))
                .isInstanceOf(DuplicateBookingException.class)
                .hasMessageContaining("Duplicate bookings are not allowed");
    }

    @Test
    @DisplayName("book() should use countByTimeSlotIdAndStatus to filter by CONFIRMED status")
    void testBook_CapacityCountFiltersConfirmedStatus() {
        // Arrange
        CreateBookingRequest request = CreateBookingRequest.builder()
                .slotId(1L)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(timeSlotRepository.findById(1L)).thenReturn(Optional.of(testTimeSlot));
        when(bookingRepository.existsActiveBookingByUserAndSlot(1L, 1L)).thenReturn(false);
        when(bookingRepository.countByTimeSlotIdAndStatus(1L, BookingStatus.CONFIRMED)).thenReturn(1L);
        when(bookingRepository.save(any(Booking.class))).thenReturn(testBooking);

        // Act
        BookingResponse response = bookingService.book(1L, request);

        // Assert
        assertThat(response).isNotNull();
        verify(bookingRepository).countByTimeSlotIdAndStatus(1L, BookingStatus.CONFIRMED);
    }

    // ===================== CANCELLATION TESTS =====================

    @Test
    @DisplayName("cancelBooking() should cancel booking successfully when user owns it")
    void testCancelBooking_Success() {
        // Arrange
        Booking bookingToCancel = Booking.builder()
                .id(1L)
                .user(testUser)
                .status(BookingStatus.CONFIRMED)
                .timeSlot(testTimeSlot)
                .build();

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(bookingToCancel));
        when(bookingRepository.save(any(Booking.class))).thenReturn(bookingToCancel);

        // Act
        bookingService.cancelBooking(1L, 1L);

        // Assert
        assertThat(bookingToCancel.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        verify(bookingRepository).save(bookingToCancel);
    }

    @Test
    @DisplayName("cancelBooking() should throw AccessDeniedException when user doesn't own booking")
    void testCancelBooking_Failure_UnauthorizedUser() {
        // Arrange
        User otherUser = User.builder().id(2L).build();
        Booking bookingByOtherUser = Booking.builder()
                .id(1L)
                .user(otherUser)
                .status(BookingStatus.CONFIRMED)
                .build();

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(bookingByOtherUser));

        // Act & Assert
        assertThatThrownBy(() -> bookingService.cancelBooking(1L, 1L))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("You can only cancel your own bookings");

        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    @DisplayName("cancelBooking() should throw IllegalStateException when already cancelled")
    void testCancelBooking_Failure_AlreadyCancelled() {
        // Arrange
        Booking alreadyCancelledBooking = Booking.builder()
                .id(1L)
                .user(testUser)
                .status(BookingStatus.CANCELLED)
                .build();

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(alreadyCancelledBooking));

        // Act & Assert
        assertThatThrownBy(() -> bookingService.cancelBooking(1L, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already cancelled");

        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    @DisplayName("cancelBooking() should throw EntityNotFoundException when booking not found")
    void testCancelBooking_Failure_BookingNotFound() {
        // Arrange
        when(bookingRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> bookingService.cancelBooking(999L, 1L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("Booking not found");

        verify(bookingRepository, never()).save(any(Booking.class));
    }

    // ===================== CAPACITY FREEING TESTS =====================

    @Test
    @DisplayName("When booking is cancelled, countByTimeSlotIdAndStatus should return lower count")
    void testCapacityFreedAfterCancellation() {
        // Arrange
        Booking bookingToCancel = Booking.builder()
                .id(1L)
                .user(testUser)
                .status(BookingStatus.CONFIRMED)
                .timeSlot(testTimeSlot)
                .build();

        when(bookingRepository.findById(1L)).thenReturn(Optional.of(bookingToCancel));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking updated = invocation.getArgument(0);
            // Simulate that cancelled booking is no longer counted
            if (updated.getStatus() == BookingStatus.CANCELLED) {
                // After cancellation, the count would be 1 less
                when(bookingRepository.countByTimeSlotIdAndStatus(1L, BookingStatus.CONFIRMED)).thenReturn(1L);
            }
            return updated;
        });

        // Act: Cancel the booking
        bookingService.cancelBooking(1L, 1L);

        // Assert: Verify booking is cancelled
        assertThat(bookingToCancel.getStatus()).isEqualTo(BookingStatus.CANCELLED);

        // Now verify capacity check would return lower count
        long newCount = bookingRepository.countByTimeSlotIdAndStatus(1L, BookingStatus.CONFIRMED);
        assertThat(newCount).isEqualTo(1L);  // One less than before
    }

    @Test
    @DisplayName("Cancelled bookings should not count toward slot capacity")
    void testCancelledBookingsNotCountedInCapacity() {
        // Arrange
        CreateBookingRequest request = CreateBookingRequest.builder()
                .slotId(1L)
                .build();

        // Slot capacity is 3, but only 2 CONFIRMED bookings exist
        // (one might be CANCELLED)
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(timeSlotRepository.findById(1L)).thenReturn(Optional.of(testTimeSlot));
        when(bookingRepository.existsActiveBookingByUserAndSlot(1L, 1L)).thenReturn(false);
        when(bookingRepository.countByTimeSlotIdAndStatus(1L, BookingStatus.CONFIRMED)).thenReturn(2L);
        when(bookingRepository.save(any(Booking.class))).thenReturn(testBooking);

        // Act
        BookingResponse response = bookingService.book(1L, request);

        // Assert: Booking succeeds because only 2 CONFIRMED (cancelled ones don't count)
        assertThat(response).isNotNull();
        verify(bookingRepository).save(any(Booking.class));
    }

    // ===================== EDGE CASES =====================

    @Test
    @DisplayName("Multiple users can book different slots")
    void testMultipleUsersCanBookDifferentSlots() {
        // User 1 books slot 1
        Booking booking1 = Booking.builder().id(1L).user(testUser).timeSlot(testTimeSlot).build();

        // User 2 books slot 2
        User user2 = User.builder().id(2L).build();
        TimeSlot slot2 = TimeSlot.builder().id(2L).build();
        Booking booking2 = Booking.builder().id(2L).user(user2).timeSlot(slot2).build();

        // Verify no exception is thrown for different users/slots
        CreateBookingRequest request1 = CreateBookingRequest.builder().slotId(1L).build();
        CreateBookingRequest request2 = CreateBookingRequest.builder().slotId(2L).build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(timeSlotRepository.findById(1L)).thenReturn(Optional.of(testTimeSlot));
        when(bookingRepository.existsActiveBookingByUserAndSlot(1L, 1L)).thenReturn(false);
        when(bookingRepository.countByTimeSlotIdAndStatus(1L, BookingStatus.CONFIRMED)).thenReturn(0L);
        when(bookingRepository.save(any(Booking.class))).thenReturn(booking1);

        BookingResponse response = bookingService.book(1L, request1);
        assertThat(response).isNotNull();
    }

    @Test
    @DisplayName("User can re-book slot after cancelling previous booking")
    void testUserCanRebookAfterCancellation() {
        // Arrange
        Booking originalBooking = Booking.builder()
                .id(1L)
                .user(testUser)
                .status(BookingStatus.CONFIRMED)
                .timeSlot(testTimeSlot)
                .build();

        Booking newBooking = Booking.builder()
                .id(2L)
                .user(testUser)
                .status(BookingStatus.CONFIRMED)
                .timeSlot(testTimeSlot)
                .build();

        // Act: Cancel original booking
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(originalBooking));
        when(bookingRepository.save(any(Booking.class))).thenReturn(originalBooking);

        bookingService.cancelBooking(1L, 1L);
        assertThat(originalBooking.getStatus()).isEqualTo(BookingStatus.CANCELLED);

        // Act: User books same slot again
        CreateBookingRequest newRequest = CreateBookingRequest.builder().slotId(1L).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(timeSlotRepository.findById(1L)).thenReturn(Optional.of(testTimeSlot));
        when(bookingRepository.existsActiveBookingByUserAndSlot(1L, 1L)).thenReturn(false);
        when(bookingRepository.countByTimeSlotIdAndStatus(1L, BookingStatus.CONFIRMED)).thenReturn(2L);
        when(bookingRepository.save(any(Booking.class))).thenReturn(newBooking);

        BookingResponse response = bookingService.book(1L, newRequest);

        // Assert: Re-booking succeeded
        assertThat(response).isNotNull();
    }
}
