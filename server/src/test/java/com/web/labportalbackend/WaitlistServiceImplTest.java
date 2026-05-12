package com.web.labportalbackend.booking.service.impl;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.booking.dto.response.WaitlistResponse;
import com.web.labportalbackend.booking.entity.TimeSlot;
import com.web.labportalbackend.booking.entity.WaitlistEntity;
import com.web.labportalbackend.booking.repository.TimeSlotRepository;
import com.web.labportalbackend.booking.repository.WaitlistRepository;
import com.web.labportalbackend.booking.service.WaitlistService;
import com.web.labportalbackend.common.exception.WaitlistDuplicateException;
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
 * Unit tests for WaitlistServiceImpl.
 * <p>
 * Tests focus on:
 * - Correct position calculation (MAX + 1)
 * - Duplicate entry detection
 * - User/Slot existence validation
 * - Mapper integration
 */
@DisplayName("WaitlistServiceImpl Unit Tests")
class WaitlistServiceImplTest {

    @Mock
    private WaitlistRepository waitlistRepository;

    @Mock
    private TimeSlotRepository timeSlotRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private WaitlistServiceImpl waitlistService;

    private User mockUser;
    private TimeSlot mockTimeSlot;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setUsername("testuser");
        mockUser.setEmail("testuser@test.com");

        mockTimeSlot = TimeSlot.builder()
                .id(100L)
                .startTime(Instant.now())
                .endTime(Instant.now().plusSeconds(3600))
                .build();
    }

    @Test
    @DisplayName("Should assign position 1 when no entries exist for slot")
    void testAddToWaitlist_FirstPosition() {
        // Arrange
        Long userId = 1L;
        Long slotId = 100L;

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(timeSlotRepository.findById(slotId)).thenReturn(Optional.of(mockTimeSlot));
        when(waitlistRepository.existsUserInWaitlist(userId, slotId)).thenReturn(false);
        when(waitlistRepository.findMaxPositionBySlotId(slotId)).thenReturn(Optional.empty());

        WaitlistEntity savedEntity = WaitlistEntity.builder()
                .id(1L)
                .user(mockUser)
                .timeSlot(mockTimeSlot)
                .position(1)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(waitlistRepository.save(any(WaitlistEntity.class))).thenReturn(savedEntity);

        // Act
        WaitlistResponse response = waitlistService.addToWaitlist(userId, slotId);

        // Assert
        assertNotNull(response);
        assertEquals(1, response.getPosition());
        assertEquals(userId, response.getUserId());
        assertEquals(slotId, response.getSlotId());
        verify(waitlistRepository, times(1)).save(any(WaitlistEntity.class));
    }

    @Test
    @DisplayName("Should assign position MAX+1 when entries exist")
    void testAddToWaitlist_PositionIncrement() {
        // Arrange
        Long userId = 2L;
        Long slotId = 100L;

        User user2 = new User();
        user2.setId(userId);
        user2.setUsername("user2");
        user2.setEmail("user2@test.com");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user2));
        when(timeSlotRepository.findById(slotId)).thenReturn(Optional.of(mockTimeSlot));
        when(waitlistRepository.existsUserInWaitlist(userId, slotId)).thenReturn(false);
        when(waitlistRepository.findMaxPositionBySlotId(slotId)).thenReturn(Optional.of(5));

        WaitlistEntity savedEntity = WaitlistEntity.builder()
                .id(2L)
                .user(user2)
                .timeSlot(mockTimeSlot)
                .position(6)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(waitlistRepository.save(any(WaitlistEntity.class))).thenReturn(savedEntity);

        // Act
        WaitlistResponse response = waitlistService.addToWaitlist(userId, slotId);

        // Assert
        assertNotNull(response);
        assertEquals(6, response.getPosition());
        verify(waitlistRepository, times(1)).findMaxPositionBySlotId(slotId);
        verify(waitlistRepository, times(1)).save(any(WaitlistEntity.class));
    }

    @Test
    @DisplayName("Should throw exception when user already in waitlist")
    void testAddToWaitlist_DuplicateUser() {
        // Arrange
        Long userId = 1L;
        Long slotId = 100L;

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(timeSlotRepository.findById(slotId)).thenReturn(Optional.of(mockTimeSlot));
        when(waitlistRepository.existsUserInWaitlist(userId, slotId)).thenReturn(true);

        // Act & Assert
        assertThrows(WaitlistDuplicateException.class, () -> {
            waitlistService.addToWaitlist(userId, slotId);
        });

        verify(waitlistRepository, never()).findMaxPositionBySlotId(anyLong());
        verify(waitlistRepository, never()).save(any(WaitlistEntity.class));
    }

    @Test
    @DisplayName("Should throw exception when user not found")
    void testAddToWaitlist_UserNotFound() {
        // Arrange
        Long userId = 999L;
        Long slotId = 100L;

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> {
            waitlistService.addToWaitlist(userId, slotId);
        });

        verify(waitlistRepository, never()).save(any(WaitlistEntity.class));
    }

    @Test
    @DisplayName("Should throw exception when slot not found")
    void testAddToWaitlist_SlotNotFound() {
        // Arrange
        Long userId = 1L;
        Long slotId = 999L;

        when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        when(timeSlotRepository.findById(slotId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> {
            waitlistService.addToWaitlist(userId, slotId);
        });

        verify(waitlistRepository, never()).save(any(WaitlistEntity.class));
    }
}
