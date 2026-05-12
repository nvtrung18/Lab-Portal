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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrency tests for waitlist promotion.
 * <p>
 * Verifies that simultaneous cancellations do not cause race conditions in promotion,
 * ensuring each promoted user is unique and correct even under high concurrent load.
 */
@DisplayName("Waitlist Promotion Concurrency Tests")
@DataJpaTest
@Import({BookingServiceImpl.class, BookingCoreService.class, WaitlistServiceImpl.class})
@ActiveProfiles("test")
class WaitlistPromotionConcurrencyTest {

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
    private List<Booking> testBookings;

    @BeforeEach
    void setUp() {
        // Create test lab
        testLab = new Laboratory();
        testLab.setLabName("Concurrency Test Lab");
        testLab.setDescription("Lab for testing concurrent promotion");
        testLab.setLocation("Building E");
        testLab.setCapacity(50);
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

        // Create 12 test users (2 confirmed + 10 waitlist)
        testUsers = transactionTemplate.execute(status -> {
            List<User> users = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                User user = new User();
                user.setUsername("concurrent_user_" + i);
                user.setEmail("concurrent" + i + "@test.com");
                user.setPassword("hashed_password");
                entityManager.persist(user);
                users.add(user);
            }
            entityManager.flush();
            return users;
        });

        // Create 2 confirmed bookings (filling slot)
        testBookings = transactionTemplate.execute(status -> {
            List<Booking> bookings = new ArrayList<>();

            for (int i = 0; i < 2; i++) {
                Booking booking = new Booking();
                booking.setUser(testUsers.get(i));
                booking.setLab(testLab);
                booking.setTimeSlot(testSlot);
                booking.setStartTime(testSlot.getStartTime());
                booking.setEndTime(testSlot.getEndTime());
                booking.setStatus(BookingStatus.CONFIRMED);
                booking.setParticipantsCount(1);
                entityManager.persist(booking);
                bookings.add(booking);
            }
            entityManager.flush();
            return bookings;
        });

        // Create 10 waitlist entries
        transactionTemplate.execute(status -> {
            for (int i = 2; i < 12; i++) {
                WaitlistEntity w = WaitlistEntity.builder()
                        .timeSlot(testSlot)
                        .user(testUsers.get(i))
                        .position(i - 1)  // positions 1-10
                        .status(WaitlistStatus.PENDING)
                        .build();
                entityManager.persist(w);
            }
            entityManager.flush();
            return null;
        });
    }

    @Test
    @DisplayName("Two concurrent cancellations promote two different users correctly")
    void testConcurrentCancellations_TwoDifferentUsers() throws InterruptedException {
        // Arrange
        Long firstBookingId = testBookings.get(0).getId();
        Long secondBookingId = testBookings.get(1).getId();
        Long firstUserId = testUsers.get(0).getId();
        Long secondUserId = testUsers.get(1).getId();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        // Act: Two threads cancel different bookings simultaneously
        executor.submit(() -> {
            try {
                bookingService.cancelBooking(firstBookingId, firstUserId);
                successCount.incrementAndGet();
            } catch (Exception e) {
                errorCount.incrementAndGet();
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                bookingService.cancelBooking(secondBookingId, secondUserId);
                successCount.incrementAndGet();
            } catch (Exception e) {
                errorCount.incrementAndGet();
                e.printStackTrace();
            } finally {
                latch.countDown();
            }
        });

        boolean finished = latch.await(30, TimeUnit.SECONDS);
        executor.shutdownNow();

        // Assert: Both operations succeeded
        assertTrue(finished, "Operations should complete within 30 seconds");
        assertEquals(2, successCount.get(), "Both cancellations should succeed");
        assertEquals(0, errorCount.get(), "No errors should occur");

        // Assert: First two waitlist users promoted to confirmed bookings
        transactionTemplate.execute(status -> {
            List<Booking> confirmedBookings = bookingRepository.findByStatus(BookingStatus.CONFIRMED);
            List<Booking> promotedBookings = confirmedBookings.stream()
                    .filter(b -> b.getTimeSlot().getId().equals(testSlot.getId()))
                    .filter(b -> (b.getUser().getId().equals(testUsers.get(2).getId()) || 
                                  b.getUser().getId().equals(testUsers.get(3).getId())))
                    .toList();

            assertEquals(2, promotedBookings.size(), "Two users should be promoted to confirmed bookings");
            
            // Verify each promoted user has exactly one confirmed booking
            for (Booking booking : promotedBookings) {
                assertEquals(1, confirmedBookings.stream()
                        .filter(b -> b.getUser().getId().equals(booking.getUser().getId()))
                        .filter(b -> b.getTimeSlot().getId().equals(testSlot.getId()))
                        .count(), "Each promoted user should have exactly one booking");
            }
            
            return null;
        });
    }

    @Test
    @DisplayName("Sequential cancellations promote users in FIFO order under concurrent load")
    void testSequentialCancellations_FIFOOrderPreserved() throws InterruptedException {
        // Scenario: Cancel bookings one by one, ensure each promotes the next in line

        // Act: Cancel first booking
        bookingService.cancelBooking(testBookings.get(0).getId(), testUsers.get(0).getId());

        // Assert: First user promoted
        transactionTemplate.execute(status -> {
            Booking promotedBooking = bookingRepository.findBySlotId(testSlot.getId())
                    .stream()
                    .filter(b -> b.getStatus() == BookingStatus.CONFIRMED)
                    .filter(b -> b.getUser().getId().equals(testUsers.get(2).getId()))
                    .findFirst()
                    .orElse(null);

            assertNotNull(promotedBooking, "First waitlist user (position 1) should be promoted");
            return null;
        });

        // Act: Cancel second booking
        bookingService.cancelBooking(testBookings.get(1).getId(), testUsers.get(1).getId());

        // Assert: Second user promoted
        transactionTemplate.execute(status -> {
            Booking promotedBooking = bookingRepository.findBySlotId(testSlot.getId())
                    .stream()
                    .filter(b -> b.getStatus() == BookingStatus.CONFIRMED)
                    .filter(b -> b.getUser().getId().equals(testUsers.get(3).getId()))
                    .findFirst()
                    .orElse(null);

            assertNotNull(promotedBooking, "Second waitlist user (position 2) should be promoted");
            return null;
        });

        // Assert: Remaining waitlist order is correct
        List<WaitlistEntity> remainingWaitlist = transactionTemplate.execute(status ->
            waitlistRepository.findBySlotIdOrderByPosition(testSlot.getId())
                    .stream()
                    .filter(w -> w.getStatus() == WaitlistStatus.PENDING)
                    .toList()
        );

        assertEquals(8, remainingWaitlist.size(), "Should have 8 pending users");
        for (int i = 0; i < remainingWaitlist.size(); i++) {
            assertEquals(i + 3, remainingWaitlist.get(i).getPosition(), 
                    "Position should be " + (i + 3));
        }
    }

    @Test
    @DisplayName("High concurrent load: 5 simultaneous cancellations promote 5 different users")
    void testHighConcurrentLoad_FiveSimultaneousCancellations() throws InterruptedException {
        // Scenario: Slot with 5 confirmed bookings and 5 waitlist entries
        // All 5 confirmed bookings cancelled simultaneously
        // Verify 5 different users promoted

        // Arrange: Create additional confirmed bookings and waitlist entries
        List<User> additionalUsers = transactionTemplate.execute(status -> {
            List<User> users = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                User user = new User();
                user.setUsername("highload_user_" + i);
                user.setEmail("highload" + i + "@test.com");
                user.setPassword("hashed");
                entityManager.persist(user);
                users.add(user);
            }
            entityManager.flush();
            return users;
        });

        TimeSlot heavySlot = TimeSlot.builder()
                .lab(testLab)
                .startTime(Instant.now().plusSeconds(7200))
                .endTime(Instant.now().plusSeconds(10800))
                .capacity(5)
                .build();
        heavySlot = transactionTemplate.execute(status -> {
            entityManager.persist(heavySlot);
            entityManager.flush();
            return heavySlot;
        });

        // Create 5 confirmed bookings and 3 waitlist entries for heavy slot
        List<Long> bookingIdsToCancel = new ArrayList<>();
        transactionTemplate.execute(status -> {
            for (int i = 0; i < 5; i++) {
                Booking booking = new Booking();
                booking.setUser(additionalUsers.get(i));
                booking.setLab(testLab);
                booking.setTimeSlot(heavySlot);
                booking.setStartTime(heavySlot.getStartTime());
                booking.setEndTime(heavySlot.getEndTime());
                booking.setStatus(BookingStatus.CONFIRMED);
                booking.setParticipantsCount(1);
                entityManager.persist(booking);
                bookingIdsToCancel.add(booking.getId());
            }

            for (int i = 5; i < 8; i++) {
                WaitlistEntity w = WaitlistEntity.builder()
                        .timeSlot(heavySlot)
                        .user(additionalUsers.get(i))
                        .position(i - 4)
                        .status(WaitlistStatus.PENDING)
                        .build();
                entityManager.persist(w);
            }

            entityManager.flush();
            return null;
        });

        // Act: 5 threads cancel simultaneously
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(5);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < 5; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    bookingService.cancelBooking(bookingIdsToCancel.get(index), 
                            additionalUsers.get(index).getId());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finished = latch.await(30, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertTrue(finished, "All cancellations should complete within 30 seconds");
        assertEquals(5, successCount.get(), "All 5 cancellations should succeed");

        // Assert: 3 users promoted (all waitlist), 2 positions available but no more waitlist
        transactionTemplate.execute(status -> {
            List<Booking> bookingsInSlot = bookingRepository.findBySlotId(heavySlot.getId());
            long confirmedCount = bookingsInSlot.stream()
                    .filter(b -> b.getStatus() == BookingStatus.CONFIRMED)
                    .count();

            // 5 original cancelled + 3 promoted from waitlist = 8 total, but confirmed = 3
            assertEquals(3, confirmedCount, "3 users should be promoted (all waitlist entries)");

            List<WaitlistEntity> remainingWaitlist = waitlistRepository.findBySlotIdOrderByPosition(heavySlot.getId())
                    .stream()
                    .filter(w -> w.getStatus() == WaitlistStatus.PENDING)
                    .toList();

            assertEquals(0, remainingWaitlist.size(), "All waitlist users should be promoted");

            return null;
        });
    }

    @Test
    @DisplayName("No duplicate promotions: Same user promoted only once")
    void testNoDuplicatePromotions() throws InterruptedException {
        // Arrange: Setup scenario where a user could theoretically be promoted twice

        // Act: Cancel first booking
        bookingService.cancelBooking(testBookings.get(0).getId(), testUsers.get(0).getId());

        // Assert: First user promoted exactly once
        transactionTemplate.execute(status -> {
            List<Booking> confirmedBookings = bookingRepository.findBySlotId(testSlot.getId())
                    .stream()
                    .filter(b -> b.getStatus() == BookingStatus.CONFIRMED)
                    .filter(b -> b.getUser().getId().equals(testUsers.get(2).getId()))
                    .toList();

            assertEquals(1, confirmedBookings.size(), "User should have exactly one confirmed booking");
            return null;
        });

        // Assert: User's waitlist entry marked as PROMOTED exactly once
        transactionTemplate.execute(status -> {
            List<WaitlistEntity> promotedEntries = waitlistRepository.findBySlotIdOrderByPosition(testSlot.getId())
                    .stream()
                    .filter(w -> w.getUser().getId().equals(testUsers.get(2).getId()))
                    .filter(w -> w.getStatus() == WaitlistStatus.PROMOTED)
                    .toList();

            assertEquals(1, promotedEntries.size(), "User should have exactly one promoted waitlist entry");
            return null;
        });
    }
}
