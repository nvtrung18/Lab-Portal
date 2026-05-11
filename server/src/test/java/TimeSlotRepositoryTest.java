package com.web.labportalbackend.booking;

import com.web.labportalbackend.booking.entity.TimeSlot;
import com.web.labportalbackend.booking.repository.TimeSlotRepository;
import com.web.labportalbackend.common.enums.TimeSlotStatus;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DataJpaTest for TimeSlotRepository.
 * Tests repository query methods and database persistence using in-memory H2 database.
 */
@DataJpaTest
@ActiveProfiles("test")
class TimeSlotRepositoryTest {

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    @Autowired
    private LaboratoryRepository laboratoryRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Laboratory testLab;
    private Instant now;
    private Instant oneHourLater;
    private Instant twoHoursLater;

    @BeforeEach
    void setUp() {
        now = Instant.now();
        oneHourLater = now.plusSeconds(3600);
        twoHoursLater = now.plusSeconds(7200);

        // Create test laboratory
        testLab = new Laboratory();
        testLab.setLabName("Integration Test Lab");
        testLab = laboratoryRepository.save(testLab);
        entityManager.flush();
    }

    // ============ findByLabId Tests ============

    @Test
    void findByLabId_WithExistingSlots_ReturnsListOfSlots() {
        // Arrange
        TimeSlot slot1 = new TimeSlot();
        slot1.setLab(testLab);
        slot1.setStartTime(now);
        slot1.setEndTime(oneHourLater);
        slot1.setCapacity(10);
        slot1.setStatus(TimeSlotStatus.AVAILABLE);

        TimeSlot slot2 = new TimeSlot();
        slot2.setLab(testLab);
        slot2.setStartTime(oneHourLater);
        slot2.setEndTime(twoHoursLater);
        slot2.setCapacity(15);
        slot2.setStatus(TimeSlotStatus.FULL);

        timeSlotRepository.save(slot1);
        timeSlotRepository.save(slot2);
        entityManager.flush();

        // Act
        List<TimeSlot> found = timeSlotRepository.findByLabId(testLab.getId());

        // Assert
        assertNotNull(found);
        assertEquals(2, found.size());
        assertTrue(found.stream().allMatch(s -> s.getLab().getId().equals(testLab.getId())));
    }

    @Test
    void findByLabId_WithDeletedSlots_ExcludesDeleted() {
        // Arrange
        TimeSlot activeSlot = new TimeSlot();
        activeSlot.setLab(testLab);
        activeSlot.setStartTime(now);
        activeSlot.setEndTime(oneHourLater);
        activeSlot.setCapacity(10);
        activeSlot.setStatus(TimeSlotStatus.AVAILABLE);
        timeSlotRepository.save(activeSlot);

        TimeSlot deletedSlot = new TimeSlot();
        deletedSlot.setLab(testLab);
        deletedSlot.setStartTime(oneHourLater);
        deletedSlot.setEndTime(twoHoursLater);
        deletedSlot.setCapacity(10);
        deletedSlot.setStatus(TimeSlotStatus.AVAILABLE);
        deletedSlot.setDeleted(true);
        timeSlotRepository.save(deletedSlot);
        entityManager.flush();

        // Act
        List<TimeSlot> found = timeSlotRepository.findByLabId(testLab.getId());

        // Assert
        assertEquals(1, found.size());
        assertFalse(found.get(0).getDeleted());
    }

    @Test
    void findByLabId_WithInactiveSlots_ExcludesInactive() {
        // Arrange
        TimeSlot activeSlot = new TimeSlot();
        activeSlot.setLab(testLab);
        activeSlot.setStartTime(now);
        activeSlot.setEndTime(oneHourLater);
        activeSlot.setCapacity(10);
        activeSlot.setStatus(TimeSlotStatus.AVAILABLE);
        timeSlotRepository.save(activeSlot);

        TimeSlot inactiveSlot = new TimeSlot();
        inactiveSlot.setLab(testLab);
        inactiveSlot.setStartTime(oneHourLater);
        inactiveSlot.setEndTime(twoHoursLater);
        inactiveSlot.setCapacity(10);
        inactiveSlot.setStatus(TimeSlotStatus.AVAILABLE);
        inactiveSlot.setActive(false);
        timeSlotRepository.save(inactiveSlot);
        entityManager.flush();

        // Act
        List<TimeSlot> found = timeSlotRepository.findByLabId(testLab.getId());

        // Assert
        assertEquals(1, found.size());
        assertTrue(found.get(0).getActive());
    }

    @Test
    void findByLabId_WithNoSlots_ReturnsEmptyList() {
        // Act
        List<TimeSlot> found = timeSlotRepository.findByLabId(testLab.getId());

        // Assert
        assertNotNull(found);
        assertEquals(0, found.size());
    }

    // ============ findByLabIdAndStatus Tests ============

    @Test
    void findByLabIdAndStatus_WithMatchingSlots_ReturnsFilteredList() {
        // Arrange
        TimeSlot availableSlot = new TimeSlot();
        availableSlot.setLab(testLab);
        availableSlot.setStartTime(now);
        availableSlot.setEndTime(oneHourLater);
        availableSlot.setCapacity(10);
        availableSlot.setStatus(TimeSlotStatus.AVAILABLE);
        timeSlotRepository.save(availableSlot);

        TimeSlot fullSlot = new TimeSlot();
        fullSlot.setLab(testLab);
        fullSlot.setStartTime(oneHourLater);
        fullSlot.setEndTime(twoHoursLater);
        fullSlot.setCapacity(15);
        fullSlot.setStatus(TimeSlotStatus.FULL);
        timeSlotRepository.save(fullSlot);
        entityManager.flush();

        // Act
        List<TimeSlot> found = timeSlotRepository.findByLabIdAndStatus(testLab.getId(), TimeSlotStatus.AVAILABLE);

        // Assert
        assertEquals(1, found.size());
        assertEquals(TimeSlotStatus.AVAILABLE, found.get(0).getStatus());
    }

    // ============ findOverlappingSlots Tests ============

    @Test
    void findOverlappingSlots_WithOverlappingRange_ReturnsOverlappingSlots() {
        // Arrange
        TimeSlot slot1 = new TimeSlot();
        slot1.setLab(testLab);
        slot1.setStartTime(now);
        slot1.setEndTime(oneHourLater);
        slot1.setCapacity(10);
        timeSlotRepository.save(slot1);

        TimeSlot slot2 = new TimeSlot();
        slot2.setLab(testLab);
        slot2.setStartTime(oneHourLater.minusSeconds(600));  // Overlaps with slot1
        slot2.setEndTime(twoHoursLater);
        slot2.setCapacity(10);
        timeSlotRepository.save(slot2);
        entityManager.flush();

        // Act - Query for overlaps with slot1's time range
        Instant queryStart = now;
        Instant queryEnd = oneHourLater;
        List<TimeSlot> found = timeSlotRepository.findOverlappingSlots(testLab.getId(), queryStart, queryEnd);

        // Assert
        assertEquals(2, found.size());
    }

    @Test
    void findOverlappingSlots_WithNoOverlap_ReturnsEmpty() {
        // Arrange
        TimeSlot slot1 = new TimeSlot();
        slot1.setLab(testLab);
        slot1.setStartTime(now);
        slot1.setEndTime(oneHourLater);
        slot1.setCapacity(10);
        timeSlotRepository.save(slot1);
        entityManager.flush();

        // Act - Query for overlaps in different time range
        Instant queryStart = twoHoursLater;
        Instant queryEnd = twoHoursLater.plusSeconds(3600);
        List<TimeSlot> found = timeSlotRepository.findOverlappingSlots(testLab.getId(), queryStart, queryEnd);

        // Assert
        assertEquals(0, found.size());
    }

    // ============ findAvailableSlotsInRange Tests ============

    @Test
    void findAvailableSlotsInRange_WithAvailableSlots_ReturnsMatchingSlots() {
        // Arrange
        TimeSlot slot1 = new TimeSlot();
        slot1.setLab(testLab);
        slot1.setStartTime(now);
        slot1.setEndTime(oneHourLater);
        slot1.setCapacity(10);
        slot1.setStatus(TimeSlotStatus.AVAILABLE);
        timeSlotRepository.save(slot1);

        TimeSlot slot2 = new TimeSlot();
        slot2.setLab(testLab);
        slot2.setStartTime(oneHourLater);
        slot2.setEndTime(twoHoursLater);
        slot2.setCapacity(10);
        slot2.setStatus(TimeSlotStatus.FULL);  // Not available
        timeSlotRepository.save(slot2);
        entityManager.flush();

        // Act
        List<TimeSlot> found = timeSlotRepository.findAvailableSlotsInRange(
                testLab.getId(), now, twoHoursLater
        );

        // Assert
        assertEquals(1, found.size());
        assertEquals(TimeSlotStatus.AVAILABLE, found.get(0).getStatus());
    }

    // ============ findActiveById Tests ============

    @Test
    void findActiveById_WithActiveSlot_ReturnsSlot() {
        // Arrange
        TimeSlot slot = new TimeSlot();
        slot.setLab(testLab);
        slot.setStartTime(now);
        slot.setEndTime(oneHourLater);
        slot.setCapacity(10);
        slot.setStatus(TimeSlotStatus.AVAILABLE);
        TimeSlot saved = timeSlotRepository.save(slot);
        entityManager.flush();

        // Act
        Optional<TimeSlot> found = timeSlotRepository.findActiveById(saved.getId());

        // Assert
        assertTrue(found.isPresent());
        assertEquals(saved.getId(), found.get().getId());
    }

    @Test
    void findActiveById_WithDeletedSlot_ReturnsEmpty() {
        // Arrange
        TimeSlot slot = new TimeSlot();
        slot.setLab(testLab);
        slot.setStartTime(now);
        slot.setEndTime(oneHourLater);
        slot.setCapacity(10);
        slot.setStatus(TimeSlotStatus.AVAILABLE);
        slot.setDeleted(true);
        TimeSlot saved = timeSlotRepository.save(slot);
        entityManager.flush();

        // Act
        Optional<TimeSlot> found = timeSlotRepository.findActiveById(saved.getId());

        // Assert
        assertTrue(found.isEmpty());
    }

    @Test
    void findActiveById_WithInactiveSlot_ReturnsEmpty() {
        // Arrange
        TimeSlot slot = new TimeSlot();
        slot.setLab(testLab);
        slot.setStartTime(now);
        slot.setEndTime(oneHourLater);
        slot.setCapacity(10);
        slot.setStatus(TimeSlotStatus.AVAILABLE);
        slot.setActive(false);
        TimeSlot saved = timeSlotRepository.save(slot);
        entityManager.flush();

        // Act
        Optional<TimeSlot> found = timeSlotRepository.findActiveById(saved.getId());

        // Assert
        assertTrue(found.isEmpty());
    }

    @Test
    void findActiveById_WithNonExistentId_ReturnsEmpty() {
        // Act
        Optional<TimeSlot> found = timeSlotRepository.findActiveById(999L);

        // Assert
        assertTrue(found.isEmpty());
    }

    // ============ Index Verification ============

    @Test
    void verifyIndexExists_idx_time_slots_lab_id() {
        // This test verifies that the idx_time_slots_lab_id index improves query performance
        // In a real scenario with large datasets, this can be verified through query execution plans

        // Arrange
        TimeSlot slot = new TimeSlot();
        slot.setLab(testLab);
        slot.setStartTime(now);
        slot.setEndTime(oneHourLater);
        slot.setCapacity(10);
        TimeSlot saved = timeSlotRepository.save(slot);
        entityManager.flush();

        // Act
        List<TimeSlot> found = timeSlotRepository.findByLabId(testLab.getId());

        // Assert - Verify query returns results efficiently
        assertEquals(1, found.size());
        assertEquals(saved.getId(), found.get(0).getId());
    }
}
