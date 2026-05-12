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
import com.web.labportalbackend.lab.entity.Laboratory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for cancelBooking() with waitlist promotion.
 * <p>
 * Tests the complete flow:
 * 1. Slot full with confirmed bookings
 * 2. Waitlist entries present
 * 3. Cancel a booking
 * 4. Verify waitlist entry promoted to confirmed booking
 * <p>
 * Uses real database (no mocks) to ensure transaction and concurrency handling.
 */
@DisplayName("BookingService.cancelBooking + Waitlist Promotion Integration Tests")
@DataJpaTest
@Import({BookingServiceImpl.class, BookingCoreService.class, WaitlistServiceImpl.class})
@ActiveProfiles("test")
class BookingCancelWaitlistPromoteIntegrationTest {

    @Autowired
    private BookingServiceImpl bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    @Autowired
    private WaitlistRepository waitlistRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private TimeSlot testSlot;
    private Laboratory testLab;
    private List<User> testUsers;
    private Booking confirmedBooking1;
    private Booking confirmedBooking2;

    @BeforeEach
    void setUp() {
        // Create test lab
        testLab = new Laboratory();
        testLab.setLabName("Promotion Test Lab");
        testLab.setDescription("Lab for testing promotion flow");
        testLab.setLocation("Building D");
        testLab.setCapacity(10);
        testLab = transactionTemplate.execute(status -> {
            entityManager.persist(testLab);
            entityManager.flush();
            return testLab;
        });

        // Create time slot with capacity=2
        testSlot = TimeSlot.builder()
                .lab(testLab)
                .startTime(Instant.now())
                .endTime(Instant.now().plusSeconds(3600))
                .capacity(2)
                .build();
        testSlot = transactionTemplate.execute(status -> {
            entityManager.persist(testSlot);
            entityManager.flush();
            return testSlot;
        });

        // Create 4 test users
        testUsers = transactionTemplate.execute(status -> {
            List<User> users = new java.util.ArrayList<>();
            for (int i = 0; i < 4; i++) {
                User user = new User();
                user.setUsername("promotion_user_" + i);
                user.setEmail("promo_user" + i + "@test.com");
                user.setPassword("hashed_password");
                entityManager.persist(user);
                users.add(user);
            }
            entityManager.flush();
            return users;
        });

        // Create 2 confirmed bookings (filling the slot)
        confirmedBooking1 = transactionTemplate.execute(status -> {
            Booking booking = new Booking();
            booking.setUser(testUsers.get(0));
            booking.setLab(testLab);
            booking.setTimeSlot(testSlot);
            booking.setStartTime(testSlot.getStartTime());
            booking.setEndTime(testSlot.getEndTime());
            booking.setStatus(BookingStatus.CONFIRMED);
            booking.setParticipantsCount(1);
            entityManager.persist(booking);
            entityManager.flush();
            return booking;
        });

        confirmedBooking2 = transactionTemplate.execute(status -> {
            Booking booking = new Booking();
            booking.setUser(testUsers.get(1));
            booking.setLab(testLab);
            booking.setTimeSlot(testSlot);
            booking.setStartTime(testSlot.getStartTime());
            booking.setEndTime(testSlot.getEndTime());
            booking.setStatus(BookingStatus.CONFIRMED);
            booking.setParticipantsCount(1);
            entityManager.persist(booking);
            entityManager.flush();
            return booking;
        });

        // Create 2 waitlist entries
        transactionTemplate.execute(status -> {
            WaitlistEntity w1 = WaitlistEntity.builder()
                    .timeSlot(testSlot)
                    .user(testUsers.get(2))
                    .position(1)
                    .status(WaitlistStatus.PENDING)
                    .build();
            entityManager.persist(w1);

            WaitlistEntity w2 = WaitlistEntity.builder()
                    .timeSlot(testSlot)
                    .user(testUsers.get(3))
                    .position(2)
                    .status(WaitlistStatus.PENDING)
                    .build();
            entityManager.persist(w2);

            entityManager.flush();
            return null;
        });
    }

    @Test
    @DisplayName("Cancel booking promotes first waitlist user to confirmed booking")
    void testCancelBooking_PromotesNextUser() {
        // Arrange
        Long bookingIdToCancel = confirmedBooking1.getId();
        Long userId = testUsers.get(0).getId();

        // Act
        bookingService.cancelBooking(bookingIdToCancel, userId);

        // Assert: Original booking is cancelled
        Booking cancelledBooking = bookingRepository.findById(bookingIdToCancel).orElse(null);
        assertNotNull(cancelledBooking);
        assertEquals(BookingStatus.CANCELLED, cancelledBooking.getStatus());

        // Assert: New booking created for first waitlist user
        Booking promotedBooking = transactionTemplate.execute(status -> {
            List<Booking> bookingsForSlot = bookingRepository.findBySlotId(testSlot.getId());
            return bookingsForSlot.stream()
                    .filter(b -> b.getStatus() == BookingStatus.CONFIRMED)
                    .filter(b -> b.getUser().getId().equals(testUsers.get(2).getId()))
                    .findFirst()
                    .orElse(null);
        });

        assertNotNull(promotedBooking, "Promoted user should have a confirmed booking");
        assertEquals(BookingStatus.CONFIRMED, promotedBooking.getStatus());
        assertEquals(testUsers.get(2).getId(), promotedBooking.getUser().getId());
        assertEquals(testSlot.getId(), promotedBooking.getTimeSlot().getId());

        // Assert: Waitlist entry marked as promoted
        WaitlistEntity promotedWaitlistEntry = transactionTemplate.execute(status -> {
            List<WaitlistEntity> entries = waitlistRepository.findBySlotIdOrderByPosition(testSlot.getId());
            return entries.stream()
                    .filter(w -> w.getUser().getId().equals(testUsers.get(2).getId()))
                    .filter(w -> w.getStatus() == WaitlistStatus.PROMOTED)
                    .findFirst()
                    .orElse(null);
        });

        assertNotNull(promotedWaitlistEntry, "Promoted user's waitlist entry should exist and be PROMOTED");
        assertEquals(WaitlistStatus.PROMOTED, promotedWaitlistEntry.getStatus());
    }

    @Test
    @DisplayName("FIFO: Only first user promoted, second user remains in waitlist")
    void testCancelBooking_FIFO_OnlyFirstUserPromoted() {
        // Arrange
        Long bookingIdToCancel = confirmedBooking1.getId();
        Long userId = testUsers.get(0).getId();

        // Act
        bookingService.cancelBooking(bookingIdToCancel, userId);

        // Assert: First waitlist user promoted
        long promotedCount = transactionTemplate.execute(status -> {
            List<WaitlistEntity> promotedEntries = waitlistRepository.findBySlotIdOrderByPosition(testSlot.getId());
            return promotedEntries.stream()
                    .filter(w -> w.getStatus() == WaitlistStatus.PROMOTED)
                    .count();
        });

        assertEquals(1, promotedCount, "Only one user should be promoted");

        // Assert: Second user still pending
        WaitlistEntity secondUserWaitlist = transactionTemplate.execute(status -> {
            return waitlistRepository.findAll()
                    .stream()
                    .filter(w -> w.getTimeSlot().getId().equals(testSlot.getId()))
                    .filter(w -> w.getUser().getId().equals(testUsers.get(3).getId()))
                    .findFirst()
                    .orElse(null);
        });

        assertNotNull(secondUserWaitlist);
        assertEquals(WaitlistStatus.PENDING, secondUserWaitlist.getStatus());
    }

    @Test
    @DisplayName("Cancel booking transaction is atomic (all or nothing)")
    void testCancelBooking_TransactionIsAtomic() {
        // Arrange
        Long bookingIdToCancel = confirmedBooking1.getId();
        Long userId = testUsers.get(0).getId();
        long initialConfirmedCount = transactionTemplate.execute(status -> 
            bookingRepository.findByStatus(BookingStatus.CONFIRMED).size()
        );

        // Act
        bookingService.cancelBooking(bookingIdToCancel, userId);

        // Assert: Confirmed count should remain same (one cancelled, one promoted)
        long finalConfirmedCount = transactionTemplate.execute(status ->
            bookingRepository.findByStatus(BookingStatus.CONFIRMED).size()
        );

        assertEquals(initialConfirmedCount, finalConfirmedCount,
                "Confirmed booking count should not change (one cancelled, one promoted)");
    }

    @Test
    @DisplayName("Slot capacity respected after promotion")
    void testCancelBooking_CapacityRespected() {
        // Arrange
        Long bookingIdToCancel = confirmedBooking1.getId();
        Long userId = testUsers.get(0).getId();

        // Act
        bookingService.cancelBooking(bookingIdToCancel, userId);

        // Assert: Confirmed count should not exceed capacity
        long confirmedCount = transactionTemplate.execute(status -> {
            List<Booking> bookings = bookingRepository.findBySlotId(testSlot.getId());
            return bookings.stream()
                    .filter(b -> b.getStatus() == BookingStatus.CONFIRMED)
                    .count();
        });

        assertTrue(confirmedCount <= testSlot.getCapacity(),
                "Confirmed bookings should not exceed slot capacity");
    }

    @Test
    @DisplayName("Waitlist entry removed/updated in order (FIFO order maintained)")
    void testCancelBooking_WaitlistOrderMaintained() {
        // Arrange
        Long bookingIdToCancel = confirmedBooking1.getId();
        Long userId = testUsers.get(0).getId();

        // Act
        bookingService.cancelBooking(bookingIdToCancel, userId);

        // Assert: Remaining pending users should have correct positions
        List<WaitlistEntity> remainingWaitlist = transactionTemplate.execute(status -> {
            List<WaitlistEntity> entries = waitlistRepository.findBySlotIdOrderByPosition(testSlot.getId());
            return entries.stream()
                    .filter(w -> w.getStatus() == WaitlistStatus.PENDING)
                    .toList();
        });

        // Should have 1 pending (position 2 user)
        assertEquals(1, remainingWaitlist.size(), "Should have 1 pending user");
        assertEquals(2, remainingWaitlist.get(0).getPosition(), "Remaining user should have position 2");
        assertEquals(testUsers.get(3).getId(), remainingWaitlist.get(0).getUser().getId());
    }

    @Test
    @DisplayName("Multiple consecutive cancellations promote all waitlist users in FIFO order")
    void testMultipleCancelBookings_AllPromotedInFIFOOrder() {
        // Scenario: Slot with 2 confirmed, 2 waitlist -> Cancel both confirmed -> Both waitlist promoted

        // Act: Cancel first booking
        bookingService.cancelBooking(confirmedBooking1.getId(), testUsers.get(0).getId());

        // Verify first user promoted
        Booking firstPromoted = transactionTemplate.execute(status -> {
            List<Booking> bookings = bookingRepository.findBySlotId(testSlot.getId());
            return bookings.stream()
                    .filter(b -> b.getStatus() == BookingStatus.CONFIRMED)
                    .filter(b -> b.getUser().getId().equals(testUsers.get(2).getId()))
                    .findFirst()
                    .orElse(null);
        });
        assertNotNull(firstPromoted, "First user should be promoted");

        // Act: Cancel second booking
        bookingService.cancelBooking(confirmedBooking2.getId(), testUsers.get(1).getId());

        // Verify second user promoted
        Booking secondPromoted = transactionTemplate.execute(status -> {
            List<Booking> bookings = bookingRepository.findBySlotId(testSlot.getId());
            return bookings.stream()
                    .filter(b -> b.getStatus() == BookingStatus.CONFIRMED)
                    .filter(b -> b.getUser().getId().equals(testUsers.get(3).getId()))
                    .findFirst()
                    .orElse(null);
        });
        assertNotNull(secondPromoted, "Second user should be promoted");

        // Assert: No pending users left
        long pendingCount = transactionTemplate.execute(status -> {
            List<WaitlistEntity> entries = waitlistRepository.findBySlotIdOrderByPosition(testSlot.getId());
            return entries.stream()
                    .filter(w -> w.getStatus() == WaitlistStatus.PENDING)
                    .count();
        });
        assertEquals(0, pendingCount, "No pending users should remain");
    }

    @Test
    @DisplayName("Promotion data integrity: All timestamps and relationships preserved")
    void testCancelBooking_DataIntegrityPreserved() {
        // Arrange
        Long bookingIdToCancel = confirmedBooking1.getId();
        Long userId = testUsers.get(0).getId();

        // Act
        bookingService.cancelBooking(bookingIdToCancel, userId);

        // Assert: Promoted booking has correct relationships
        Booking promotedBooking = transactionTemplate.execute(status -> {
            List<Booking> bookings = bookingRepository.findBySlotId(testSlot.getId());
            return bookings.stream()
                    .filter(b -> b.getUser().getId().equals(testUsers.get(2).getId()))
                    .filter(b -> b.getStatus() == BookingStatus.CONFIRMED)
                    .findFirst()
                    .orElse(null);
        });

        assertNotNull(promotedBooking);
        assertEquals(testUsers.get(2).getId(), promotedBooking.getUser().getId());
        assertEquals(testSlot.getId(), promotedBooking.getTimeSlot().getId());
        assertEquals(testLab.getId(), promotedBooking.getLab().getId());
        assertEquals(testSlot.getStartTime(), promotedBooking.getStartTime());
        assertEquals(testSlot.getEndTime(), promotedBooking.getEndTime());
    }
}
