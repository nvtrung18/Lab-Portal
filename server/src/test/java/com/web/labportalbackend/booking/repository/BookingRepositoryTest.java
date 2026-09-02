package com.web.labportalbackend.booking.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.booking.entity.Booking;
import com.web.labportalbackend.booking.entity.TimeSlot;
import com.web.labportalbackend.common.enums.BookingStatus;
import com.web.labportalbackend.common.enums.LabStatus;
import com.web.labportalbackend.common.enums.TimeSlotStatus;
import com.web.labportalbackend.common.enums.UserStatus;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class BookingRepositoryTest {

    @Autowired BookingRepository bookingRepository;
    @Autowired TimeSlotRepository timeSlotRepository;
    @Autowired LaboratoryRepository laboratoryRepository;
    @Autowired UserRepository userRepository;

    @Test
    void aggregateCountsPreserveSlotStatusSemanticsAndExcludeInactiveRows() {
        Laboratory lab = laboratoryRepository.saveAndFlush(new Laboratory(
                "Aggregate Test Lab", null, "Room A", 20, "Engineering", LabStatus.AVAILABLE, null));
        TimeSlot slot = timeSlotRepository.saveAndFlush(TimeSlot.builder()
                .lab(lab)
                .startTime(Instant.parse("2026-09-03T01:00:00Z"))
                .endTime(Instant.parse("2026-09-03T02:00:00Z"))
                .capacity(10)
                .status(TimeSlotStatus.AVAILABLE)
                .build());

        saveBooking(slot, BookingStatus.APPROVED, "approved", true, false);
        saveBooking(slot, BookingStatus.CHECKED_IN, "checked-in", true, false);
        saveBooking(slot, BookingStatus.PENDING_APPROVAL, "pending", true, false);
        saveBooking(slot, BookingStatus.REJECTED, "rejected", true, false);
        saveBooking(slot, BookingStatus.APPROVED, "deleted", true, true);
        saveBooking(slot, BookingStatus.CHECKED_IN, "inactive", false, false);

        List<TimeSlotBookingCounts> result = bookingRepository.findActiveCountsByTimeSlotIds(List.of(slot.getId()));

        assertEquals(1, result.size());
        TimeSlotBookingCounts counts = result.getFirst();
        assertEquals(slot.getId(), counts.timeSlotId());
        assertEquals(2L, counts.approvedCount());
        assertEquals(1L, counts.checkedInCount());
        assertEquals(1L, counts.pendingCount());
    }

    @Test
    void aggregateCountsReturnNoRowsForEmptySlotSelection() {
        assertTrue(bookingRepository.findActiveCountsByTimeSlotIds(List.of()).isEmpty());
    }

    private void saveBooking(
            TimeSlot slot,
            BookingStatus status,
            String username,
            boolean active,
            boolean deleted
    ) {
        User user = userRepository.saveAndFlush(new User(
                username + "@example.test", username, "password", username,
                null, UserStatus.ACTIVE, new HashSet<>()));
        Booking booking = new Booking();
        booking.setUser(user);
        booking.setLab(slot.getLab());
        booking.setTimeSlot(slot);
        booking.setStartTime(slot.getStartTime());
        booking.setEndTime(slot.getEndTime());
        booking.setStatus(status);
        booking.setPurpose("Repository aggregate test");
        booking.setParticipantsCount(1);
        booking.setActive(active);
        booking.setDeleted(deleted);
        bookingRepository.saveAndFlush(booking);
    }
}
