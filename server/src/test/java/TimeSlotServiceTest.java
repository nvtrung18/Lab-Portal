package com.web.labportalbackend.booking;

import com.web.labportalbackend.booking.entity.TimeSlot;
import com.web.labportalbackend.booking.repository.TimeSlotRepository;
import com.web.labportalbackend.common.dto.CreateTimeSlotRequest;
import com.web.labportalbackend.common.dto.TimeSlotResponse;
import com.web.labportalbackend.common.enums.TimeSlotStatus;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TimeSlotServiceImpl.
 * Tests business logic for time slot creation, retrieval, and status management.
 */
@ExtendWith(MockitoExtension.class)
class TimeSlotServiceTest {

    @Mock
    private TimeSlotRepository timeSlotRepository;

    @Mock
    private LaboratoryRepository laboratoryRepository;

    @InjectMocks
    private TimeSlotServiceImpl timeSlotService;

    private Laboratory testLab;
    private TimeSlot testTimeSlot;
    private Instant now;
    private Instant oneHourLater;

    @BeforeEach
    void setUp() {
        now = Instant.now();
        oneHourLater = now.plusSeconds(3600);

        // Setup test laboratory
        testLab = new Laboratory();
        testLab.setId(1L);
        testLab.setLabName("Test Lab");

        // Setup test time slot
        testTimeSlot = new TimeSlot();
        testTimeSlot.setId(1L);
        testTimeSlot.setLab(testLab);
        testTimeSlot.setStartTime(now);
        testTimeSlot.setEndTime(oneHourLater);
        testTimeSlot.setCapacity(10);
        testTimeSlot.setStatus(TimeSlotStatus.AVAILABLE);
        testTimeSlot.setCreatedAt(now);
        testTimeSlot.setUpdatedAt(now);
        testTimeSlot.setActive(true);
        testTimeSlot.setDeleted(false);
    }

    // ============ createSlot Tests ============

    @Test
    void createSlot_WithValidRequest_ReturnsTimeSlotResponse() {
        // Arrange
        CreateTimeSlotRequest request = CreateTimeSlotRequest.builder()
                .labId(1L)
                .startTime(now)
                .endTime(oneHourLater)
                .capacity(10)
                .build();

        when(laboratoryRepository.findById(1L)).thenReturn(Optional.of(testLab));
        when(timeSlotRepository.save(any(TimeSlot.class))).thenReturn(testTimeSlot);

        // Act
        TimeSlotResponse response = timeSlotService.createSlot(request);

        // Assert
        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals(10, response.getCapacity());
        assertEquals(TimeSlotStatus.AVAILABLE, response.getStatus());
        verify(timeSlotRepository, times(1)).save(any(TimeSlot.class));
    }

    @Test
    void createSlot_WithInvalidLabId_ThrowsEntityNotFoundException() {
        // Arrange
        CreateTimeSlotRequest request = CreateTimeSlotRequest.builder()
                .labId(999L)
                .startTime(now)
                .endTime(oneHourLater)
                .capacity(10)
                .build();

        when(laboratoryRepository.findById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> timeSlotService.createSlot(request));
        verify(timeSlotRepository, never()).save(any());
    }

    @Test
    void createSlot_WithStartTimeAfterEndTime_ThrowsIllegalArgumentException() {
        // Arrange
        CreateTimeSlotRequest request = CreateTimeSlotRequest.builder()
                .labId(1L)
                .startTime(oneHourLater)
                .endTime(now)  // End time is before start time
                .capacity(10)
                .build();

        when(laboratoryRepository.findById(1L)).thenReturn(Optional.of(testLab));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> timeSlotService.createSlot(request));
        verify(timeSlotRepository, never()).save(any());
    }

    @Test
    void createSlot_WithStartTimeEqualToEndTime_ThrowsIllegalArgumentException() {
        // Arrange
        CreateTimeSlotRequest request = CreateTimeSlotRequest.builder()
                .labId(1L)
                .startTime(now)
                .endTime(now)  // Same time
                .capacity(10)
                .build();

        when(laboratoryRepository.findById(1L)).thenReturn(Optional.of(testLab));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> timeSlotService.createSlot(request));
        verify(timeSlotRepository, never()).save(any());
    }

    @Test
    void createSlot_WithZeroCapacity_ThrowsIllegalArgumentException() {
        // Arrange
        CreateTimeSlotRequest request = CreateTimeSlotRequest.builder()
                .labId(1L)
                .startTime(now)
                .endTime(oneHourLater)
                .capacity(0)  // Invalid capacity
                .build();

        when(laboratoryRepository.findById(1L)).thenReturn(Optional.of(testLab));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> timeSlotService.createSlot(request));
        verify(timeSlotRepository, never()).save(any());
    }

    @Test
    void createSlot_WithNegativeCapacity_ThrowsIllegalArgumentException() {
        // Arrange
        CreateTimeSlotRequest request = CreateTimeSlotRequest.builder()
                .labId(1L)
                .startTime(now)
                .endTime(oneHourLater)
                .capacity(-5)  // Invalid capacity
                .build();

        when(laboratoryRepository.findById(1L)).thenReturn(Optional.of(testLab));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> timeSlotService.createSlot(request));
        verify(timeSlotRepository, never()).save(any());
    }

    // ============ getSlotsByLab Tests ============

    @Test
    void getSlotsByLab_WithValidLabId_ReturnsListOfTimeSlots() {
        // Arrange
        TimeSlot slot2 = new TimeSlot();
        slot2.setId(2L);
        slot2.setLab(testLab);
        slot2.setStartTime(oneHourLater);
        slot2.setEndTime(oneHourLater.plusSeconds(3600));
        slot2.setCapacity(15);
        slot2.setStatus(TimeSlotStatus.FULL);
        slot2.setCreatedAt(oneHourLater);
        slot2.setUpdatedAt(oneHourLater);
        slot2.setActive(true);
        slot2.setDeleted(false);

        List<TimeSlot> slots = Arrays.asList(testTimeSlot, slot2);

        when(laboratoryRepository.existsById(1L)).thenReturn(true);
        when(timeSlotRepository.findByLabId(1L)).thenReturn(slots);

        // Act
        List<TimeSlotResponse> responses = timeSlotService.getSlotsByLab(1L);

        // Assert
        assertNotNull(responses);
        assertEquals(2, responses.size());
        assertEquals(10, responses.get(0).getCapacity());
        assertEquals(15, responses.get(1).getCapacity());
        verify(timeSlotRepository, times(1)).findByLabId(1L);
    }

    @Test
    void getSlotsByLab_WithValidLabIdButNoSlots_ReturnsEmptyList() {
        // Arrange
        when(laboratoryRepository.existsById(1L)).thenReturn(true);
        when(timeSlotRepository.findByLabId(1L)).thenReturn(Arrays.asList());

        // Act
        List<TimeSlotResponse> responses = timeSlotService.getSlotsByLab(1L);

        // Assert
        assertNotNull(responses);
        assertEquals(0, responses.size());
        verify(timeSlotRepository, times(1)).findByLabId(1L);
    }

    @Test
    void getSlotsByLab_WithInvalidLabId_ThrowsEntityNotFoundException() {
        // Arrange
        when(laboratoryRepository.existsById(999L)).thenReturn(false);

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> timeSlotService.getSlotsByLab(999L));
        verify(timeSlotRepository, never()).findByLabId(any());
    }

    // ============ getSlotById Tests ============

    @Test
    void getSlotById_WithValidId_ReturnsTimeSlotResponse() {
        // Arrange
        when(timeSlotRepository.findActiveById(1L)).thenReturn(Optional.of(testTimeSlot));

        // Act
        TimeSlotResponse response = timeSlotService.getSlotById(1L);

        // Assert
        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals(10, response.getCapacity());
        verify(timeSlotRepository, times(1)).findActiveById(1L);
    }

    @Test
    void getSlotById_WithInvalidId_ThrowsEntityNotFoundException() {
        // Arrange
        when(timeSlotRepository.findActiveById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> timeSlotService.getSlotById(999L));
    }

    // ============ updateSlotStatus Tests ============

    @Test
    void updateSlotStatus_WithValidStatus_ReturnsUpdatedTimeSlot() {
        // Arrange
        TimeSlot updatedSlot = new TimeSlot();
        updatedSlot.setId(1L);
        updatedSlot.setLab(testLab);
        updatedSlot.setStartTime(now);
        updatedSlot.setEndTime(oneHourLater);
        updatedSlot.setCapacity(10);
        updatedSlot.setStatus(TimeSlotStatus.FULL);
        updatedSlot.setCreatedAt(now);
        updatedSlot.setUpdatedAt(Instant.now());
        updatedSlot.setActive(true);
        updatedSlot.setDeleted(false);

        when(timeSlotRepository.findActiveById(1L)).thenReturn(Optional.of(testTimeSlot));
        when(timeSlotRepository.save(any(TimeSlot.class))).thenReturn(updatedSlot);

        // Act
        TimeSlotResponse response = timeSlotService.updateSlotStatus(1L, "FULL");

        // Assert
        assertNotNull(response);
        assertEquals(TimeSlotStatus.FULL, response.getStatus());
        verify(timeSlotRepository, times(1)).save(any(TimeSlot.class));
    }

    @Test
    void updateSlotStatus_WithInvalidStatus_ThrowsIllegalArgumentException() {
        // Arrange
        when(timeSlotRepository.findActiveById(1L)).thenReturn(Optional.of(testTimeSlot));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> timeSlotService.updateSlotStatus(1L, "INVALID"));
        verify(timeSlotRepository, never()).save(any());
    }

    @Test
    void updateSlotStatus_WithCancelledStatus_UpdatesSuccessfully() {
        // Arrange
        TimeSlot cancelledSlot = new TimeSlot();
        cancelledSlot.setId(1L);
        cancelledSlot.setLab(testLab);
        cancelledSlot.setStartTime(now);
        cancelledSlot.setEndTime(oneHourLater);
        cancelledSlot.setCapacity(10);
        cancelledSlot.setStatus(TimeSlotStatus.CANCELLED);
        cancelledSlot.setCreatedAt(now);
        cancelledSlot.setUpdatedAt(Instant.now());
        cancelledSlot.setActive(true);
        cancelledSlot.setDeleted(false);

        when(timeSlotRepository.findActiveById(1L)).thenReturn(Optional.of(testTimeSlot));
        when(timeSlotRepository.save(any(TimeSlot.class))).thenReturn(cancelledSlot);

        // Act
        TimeSlotResponse response = timeSlotService.updateSlotStatus(1L, "CANCELLED");

        // Assert
        assertNotNull(response);
        assertEquals(TimeSlotStatus.CANCELLED, response.getStatus());
    }

    // ============ deleteSlot Tests ============

    @Test
    void deleteSlot_WithValidId_SoftDeletesSlot() {
        // Arrange
        when(timeSlotRepository.findActiveById(1L)).thenReturn(Optional.of(testTimeSlot));
        when(timeSlotRepository.save(any(TimeSlot.class))).thenReturn(testTimeSlot);

        // Act
        timeSlotService.deleteSlot(1L);

        // Assert
        verify(timeSlotRepository, times(1)).save(any(TimeSlot.class));
    }

    @Test
    void deleteSlot_WithInvalidId_ThrowsEntityNotFoundException() {
        // Arrange
        when(timeSlotRepository.findActiveById(999L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(EntityNotFoundException.class, () -> timeSlotService.deleteSlot(999L));
        verify(timeSlotRepository, never()).save(any());
    }
}
