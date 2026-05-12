package com.web.labportalbackend.booking.service.impl;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.booking.entity.Booking;
import com.web.labportalbackend.booking.entity.TimeSlot;
import com.web.labportalbackend.booking.entity.WaitlistEntity;
import com.web.labportalbackend.booking.repository.BookingRepository;
import com.web.labportalbackend.booking.repository.TimeSlotRepository;
import com.web.labportalbackend.booking.repository.WaitlistRepository;
import com.web.labportalbackend.common.enums.BookingStatus;
import com.web.labportalbackend.common.enums.WaitlistStatus;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WaitlistServiceImpl.promoteNext() method.
 * <p>
 * Tests focus on:
 * - FIFO promotion (position 1 promoted)
 * - Edge case: User already has booking (skip and promote next)
 * - Empty waitlist (graceful exit)
 * - Status update to PROMOTED
 * - New booking creation with CONFIRMED status
 */
@DisplayName("WaitlistServiceImpl.promoteNext Unit Tests")
class WaitlistServiceImplPromoteTest {

    @Mock
    private WaitlistRepository waitlistRepository;

    @Mock
    private TimeSlotRepository timeSlotRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BookingRepository bookingRepository;

    @InjectMocks
    private WaitlistServiceImpl waitlistService;

    private TimeSlot mockTimeSlot;
    private User mockUser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        mockTimeSlot = TimeSlot.builder()
                .id(100L)
                .startTime(Instant.now())
                .endTime(Instant.now().plusSeconds(3600))
                .capacity(2)
                .build();

        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setUsername("user1");
        mockUser.setEmail("user1@test.com");
    }

    @Test
    @DisplayName("Should promote first waitlist user to confirmed booking")
    void testPromoteNext_FirstUser_Success() {
        // Arrange
        Long slotId = 100L;
        WaitlistEntity waitlistEntry = WaitlistEntity.builder()
                .id(1L)
                .timeSlot(mockTimeSlot)
                .user(mockUser)
                .position(1)
                .status(WaitlistStatus.PENDING)
                .build();

        when(waitlistRepository.findFirstBySlotIdAndStatusOrderByPositionAsc(slotId, WaitlistStatus.PENDING))
                .thenReturn(Optional.of(waitlistEntry));

        when(bookingRepository.existsActiveBookingByUserAndSlot(mockUser.getId(), slotId))
                .thenReturn(false);

        when(timeSlotRepository.findById(slotId))
                .thenReturn(Optional.of(mockTimeSlot));

        Booking savedBooking = new Booking();
        savedBooking.setId(200L);
        when(bookingRepository.save(any(Booking.class)))
                .thenReturn(savedBooking);

        when(waitlistRepository.save(any(WaitlistEntity.class)))
                .thenReturn(waitlistEntry);

        // Act
        waitlistService.promoteNext(slotId);

        // Assert
        verify(waitlistRepository, times(1)).findFirstBySlotIdAndStatusOrderByPositionAsc(slotId, WaitlistStatus.PENDING);
        verify(bookingRepository, times(1)).existsActiveBookingByUserAndSlot(mockUser.getId(), slotId);
        verify(timeSlotRepository, times(1)).findById(slotId);
        verify(bookingRepository, times(1)).save(any(Booking.class));
        verify(waitlistRepository, times(1)).save(waitlistEntry);

        // Verify status was changed to PROMOTED
        assertEquals(WaitlistStatus.PROMOTED, waitlistEntry.getStatus());
    }

    @Test
    @DisplayName("Should skip user with existing booking and not promote")
    void testPromoteNext_UserAlreadyBooked_SkipAndTryNext() {
        // Arrange
        Long slotId = 100L;

        User user1 = new User();
        user1.setId(1L);
        user1.setUsername("user1");

        User user2 = new User();
        user2.setId(2L);
        user2.setUsername("user2");

        WaitlistEntity entry1 = WaitlistEntity.builder()
                .id(1L)
                .timeSlot(mockTimeSlot)
                .user(user1)
                .position(1)
                .status(WaitlistStatus.PENDING)
                .build();

        WaitlistEntity entry2 = WaitlistEntity.builder()
                .id(2L)
                .timeSlot(mockTimeSlot)
                .user(user2)
                .position(2)
                .status(WaitlistStatus.PENDING)
                .build();

        // First call: user1 already has booking
        // Second call: user2 is valid
        when(waitlistRepository.findFirstBySlotIdAndStatusOrderByPositionAsc(slotId, WaitlistStatus.PENDING))
                .thenReturn(Optional.of(entry1))
                .thenReturn(Optional.of(entry2));

        when(bookingRepository.existsActiveBookingByUserAndSlot(user1.getId(), slotId))
                .thenReturn(true);  // User1 already has booking

        when(bookingRepository.existsActiveBookingByUserAndSlot(user2.getId(), slotId))
                .thenReturn(false);  // User2 is valid

        when(timeSlotRepository.findById(slotId))
                .thenReturn(Optional.of(mockTimeSlot));

        Booking savedBooking = new Booking();
        savedBooking.setId(200L);
        when(bookingRepository.save(any(Booking.class)))
                .thenReturn(savedBooking);

        when(waitlistRepository.save(any(WaitlistEntity.class)))
                .thenReturn(entry1)
                .thenReturn(entry2);

        // Act
        waitlistService.promoteNext(slotId);

        // Assert
        // Should find first user (user1)
        verify(waitlistRepository, times(2)).findFirstBySlotIdAndStatusOrderByPositionAsc(slotId, WaitlistStatus.PENDING);
        
        // Should check both users
        verify(bookingRepository, times(1)).existsActiveBookingByUserAndSlot(user1.getId(), slotId);
        verify(bookingRepository, times(1)).existsActiveBookingByUserAndSlot(user2.getId(), slotId);

        // Should create booking for user2 (second one)
        verify(bookingRepository, times(1)).save(any(Booking.class));
        
        // Should update both: entry1 to PROMOTED (skip), entry2 to PROMOTED (promote)
        verify(waitlistRepository, times(2)).save(any(WaitlistEntity.class));
    }

    @Test
    @DisplayName("Should return gracefully when no pending users in waitlist")
    void testPromoteNext_EmptyWaitlist_ReturnsGracefully() {
        // Arrange
        Long slotId = 100L;

        when(waitlistRepository.findFirstBySlotIdAndStatusOrderByPositionAsc(slotId, WaitlistStatus.PENDING))
                .thenReturn(Optional.empty());

        // Act
        waitlistService.promoteNext(slotId);

        // Assert
        verify(waitlistRepository, times(1)).findFirstBySlotIdAndStatusOrderByPositionAsc(slotId, WaitlistStatus.PENDING);
        verify(bookingRepository, never()).existsActiveBookingByUserAndSlot(anyLong(), anyLong());
        verify(bookingRepository, never()).save(any(Booking.class));
        verify(timeSlotRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("Should throw exception when slot not found")
    void testPromoteNext_SlotNotFound_ThrowsException() {
        // Arrange
        Long slotId = 100L;
        Long userId = 1L;

        WaitlistEntity waitlistEntry = WaitlistEntity.builder()
                .id(1L)
                .timeSlot(mockTimeSlot)
                .user(mockUser)
                .position(1)
                .status(WaitlistStatus.PENDING)
                .build();

        when(waitlistRepository.findFirstBySlotIdAndStatusOrderByPositionAsc(slotId, WaitlistStatus.PENDING))
                .thenReturn(Optional.of(waitlistEntry));

        when(bookingRepository.existsActiveBookingByUserAndSlot(userId, slotId))
                .thenReturn(false);

        when(timeSlotRepository.findById(slotId))
                .thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> {
            waitlistService.promoteNext(slotId);
        });

        verify(timeSlotRepository, times(1)).findById(slotId);
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    @DisplayName("Should create booking with correct attributes")
    void testPromoteNext_BookingCreatedWithCorrectAttributes() {
        // Arrange
        Long slotId = 100L;
        Instant startTime = Instant.now();
        Instant endTime = startTime.plusSeconds(3600);

        mockTimeSlot.setStartTime(startTime);
        mockTimeSlot.setEndTime(endTime);

        WaitlistEntity waitlistEntry = WaitlistEntity.builder()
                .id(1L)
                .timeSlot(mockTimeSlot)
                .user(mockUser)
                .position(1)
                .status(WaitlistStatus.PENDING)
                .build();

        when(waitlistRepository.findFirstBySlotIdAndStatusOrderByPositionAsc(slotId, WaitlistStatus.PENDING))
                .thenReturn(Optional.of(waitlistEntry));

        when(bookingRepository.existsActiveBookingByUserAndSlot(mockUser.getId(), slotId))
                .thenReturn(false);

        when(timeSlotRepository.findById(slotId))
                .thenReturn(Optional.of(mockTimeSlot));

        Booking savedBooking = new Booking();
        savedBooking.setId(200L);
        when(bookingRepository.save(any(Booking.class)))
                .thenAnswer(invocation -> {
                    Booking booking = invocation.getArgument(0);
                    
                    // Verify booking attributes
                    assertEquals(mockUser, booking.getUser());
                    assertEquals(mockTimeSlot.getLab(), booking.getLab());
                    assertEquals(mockTimeSlot, booking.getTimeSlot());
                    assertEquals(startTime, booking.getStartTime());
                    assertEquals(endTime, booking.getEndTime());
                    assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
                    assertEquals(1, booking.getParticipantsCount());
                    
                    return savedBooking;
                });

        when(waitlistRepository.save(any(WaitlistEntity.class)))
                .thenReturn(waitlistEntry);

        // Act
        waitlistService.promoteNext(slotId);

        // Assert
        verify(bookingRepository, times(1)).save(any(Booking.class));
    }

    @Test
    @DisplayName("Should update waitlist status to PROMOTED")
    void testPromoteNext_WaitlistStatusUpdated() {
        // Arrange
        Long slotId = 100L;

        WaitlistEntity waitlistEntry = WaitlistEntity.builder()
                .id(1L)
                .timeSlot(mockTimeSlot)
                .user(mockUser)
                .position(1)
                .status(WaitlistStatus.PENDING)
                .build();

        when(waitlistRepository.findFirstBySlotIdAndStatusOrderByPositionAsc(slotId, WaitlistStatus.PENDING))
                .thenReturn(Optional.of(waitlistEntry));

        when(bookingRepository.existsActiveBookingByUserAndSlot(mockUser.getId(), slotId))
                .thenReturn(false);

        when(timeSlotRepository.findById(slotId))
                .thenReturn(Optional.of(mockTimeSlot));

        Booking savedBooking = new Booking();
        savedBooking.setId(200L);
        when(bookingRepository.save(any(Booking.class)))
                .thenReturn(savedBooking);

        when(waitlistRepository.save(any(WaitlistEntity.class)))
                .thenAnswer(invocation -> {
                    WaitlistEntity entry = invocation.getArgument(0);
                    assertEquals(WaitlistStatus.PROMOTED, entry.getStatus());
                    return entry;
                });

        // Act
        waitlistService.promoteNext(slotId);

        // Assert
        verify(waitlistRepository, times(1)).save(any(WaitlistEntity.class));
    }
}
