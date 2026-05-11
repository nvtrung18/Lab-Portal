package com.web.labportalbackend.booking.service;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.booking.BookingCoreService;
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
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

/**
 * Concurrency integration tests for BookingService.
 * Tests pessimistic locking, retry logic, and fallback to waitlist under high-concurrency scenarios.
 * 
 * Test Scenarios:
 * 1. Overbooking Prevention: Multiple threads attempting to book a single slot
 * 2. Capacity Protection: Verify only CONFIRMED bookings count toward capacity
 * 3. Retry Logic: Verify retry mechanism works correctly
 * 4. Waitlist Fallback: Verify users are moved to waitlist when booking fails
 */
@Slf4j
@DataJpaTest
@ActiveProfiles("test")
@Import({BookingServiceImpl.class, BookingCoreService.class})
@DisplayName("BookingService Concurrency Tests")
class BookingServiceConcurrencyTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Lab lab;
    private TimeSlot slot;
    private List<User> users;
    private static final int SLOT_CAPACITY = 2;
    private static final int NUM_THREADS = 10;

    @BeforeEach
    void setUp() {
        // Create lab
        lab = new Lab();
        lab.setName("Concurrency Lab");
        lab.setLocation("Building A");
        lab.setActive(true);
        lab.setDeleted(false);
        entityManager.persistAndFlush(lab);

        // Create time slot with limited capacity
        slot = new TimeSlot();
        slot.setLab(lab);
        slot.setStartTime(Instant.now().plusSeconds(3600));
        slot.setEndTime(Instant.now().plusSeconds(7200));
        slot.setCapacity(SLOT_CAPACITY);
        slot.setStatus(TimeSlotStatus.AVAILABLE);
        slot.setActive(true);
        slot.setDeleted(false);
        entityManager.persistAndFlush(slot);

        // Create test users
        users = new ArrayList<>();
        for (int i = 0; i < NUM_THREADS; i++) {
            User user = new User();
            user.setEmail("user" + i + "@test.com");
            user.setUsername("testuser" + i);
            user.setPassword("password");
            user.setActive(true);
            user.setDeleted(false);
            entityManager.persistAndFlush(user);
            users.add(user);
        }

        entityManager.clear();
    }

    @Test
    @DisplayName("Scenario 1: Overbooking Prevention - Multiple threads booking single slot")
    @Timeout(30)  // 30-second timeout for the entire test
    void testOverbookingPrevention() throws InterruptedException {
        log.info("=== Starting Overbooking Prevention Test ===");
        log.info("Slot capacity: {}, Number of threads: {}", SLOT_CAPACITY, NUM_THREADS);

        ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
        CountDownLatch startLatch = new CountDownLatch(1);  // All threads wait for signal to start
        CountDownLatch endLatch = new CountDownLatch(NUM_THREADS);  // Main thread waits for all to finish

        AtomicInteger confirmedCount = new AtomicInteger(0);
        AtomicInteger waitlistedCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // Submit tasks for all users to book the same slot
        for (int i = 0; i < NUM_THREADS; i++) {
            final int userIndex = i;
            executor.submit(() -> {
                try {
                    // Wait for signal to start (all threads synchronized)
                    startLatch.await();

                    User user = users.get(userIndex);
                    CreateBookingRequest request = new CreateBookingRequest();
                    request.setSlotId(slot.getId());

                    long startTime = System.currentTimeMillis();
                    BookingResponse response = bookingService.book(user.getId(), request);
                    long endTime = System.currentTimeMillis();

                    log.info("Thread {}: Booking {} - Status: {}, Duration: {}ms",
                            userIndex, response.getId(), response.getStatus(), (endTime - startTime));

                    if (response.getStatus() == BookingStatus.CONFIRMED) {
                        confirmedCount.incrementAndGet();
                    } else if (response.getStatus() == BookingStatus.WAITLISTED) {
                        waitlistedCount.incrementAndGet();
                    }

                } catch (Exception e) {
                    log.error("Thread {}: Booking failed with exception: {}", userIndex, e.getMessage());
                    failureCount.incrementAndGet();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // Start all threads at the same time
        startLatch.countDown();

        // Wait for all threads to complete
        boolean completed = endLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        log.info("=== Test Results ===");
        log.info("All threads completed: {}", completed);
        log.info("CONFIRMED bookings: {}", confirmedCount.get());
        log.info("WAITLISTED bookings: {}", waitlistedCount.get());
        log.info("Failures: {}", failureCount.get());

        // Verify results
        assertThat(completed).as("All threads should complete within timeout").isTrue();
        assertThat(confirmedCount.get())
                .as("Should have exactly {} CONFIRMED bookings (slot capacity)", SLOT_CAPACITY)
                .isEqualTo(SLOT_CAPACITY);
        assertThat(waitlistedCount.get())
                .as("Should have {} WAITLISTED bookings (NUM_THREADS - capacity)", NUM_THREADS - SLOT_CAPACITY)
                .isEqualTo(NUM_THREADS - SLOT_CAPACITY);
        assertThat(failureCount.get())
                .as("Should have no failures")
                .isZero();

        // Verify database state
        long confirmedInDb = bookingRepository.countByTimeSlotIdAndStatus(slot.getId(), BookingStatus.CONFIRMED);
        long waitlistedInDb = bookingRepository.countByTimeSlotIdAndStatus(slot.getId(), BookingStatus.WAITLISTED);

        assertThat(confirmedInDb)
                .as("Database should have exactly {} CONFIRMED bookings", SLOT_CAPACITY)
                .isEqualTo(SLOT_CAPACITY);
        assertThat(waitlistedInDb)
                .as("Database should have {} WAITLISTED bookings", NUM_THREADS - SLOT_CAPACITY)
                .isEqualTo(NUM_THREADS - SLOT_CAPACITY);

        log.info("=== Overbooking Prevention Test PASSED ===");
    }

    @Test
    @DisplayName("Scenario 2: Capacity Protected - Only CONFIRMED bookings count")
    @Timeout(30)
    void testCapacityProtectionWithWaitlist() throws InterruptedException {
        log.info("=== Starting Capacity Protection Test ===");

        // First, fill the slot with CONFIRMED bookings
        ExecutorService executor = Executors.newFixedThreadPool(SLOT_CAPACITY);
        CountDownLatch confirmLatch = new CountDownLatch(SLOT_CAPACITY);
        CountDownLatch endLatch = new CountDownLatch(NUM_THREADS);

        AtomicInteger confirmedCount = new AtomicInteger(0);
        AtomicInteger waitlistedCount = new AtomicInteger(0);

        // All users try to book simultaneously
        for (int i = 0; i < NUM_THREADS; i++) {
            final int userIndex = i;
            executor.submit(() -> {
                try {
                    User user = users.get(userIndex);
                    CreateBookingRequest request = new CreateBookingRequest();
                    request.setSlotId(slot.getId());

                    BookingResponse response = bookingService.book(user.getId(), request);

                    if (response.getStatus() == BookingStatus.CONFIRMED) {
                        confirmedCount.incrementAndGet();
                        confirmLatch.countDown();
                    } else if (response.getStatus() == BookingStatus.WAITLISTED) {
                        waitlistedCount.incrementAndGet();
                    }

                    log.debug("User {}: {} booking created (status: {})", userIndex, response.getId(), response.getStatus());

                } catch (Exception e) {
                    log.error("User {} booking failed: {}", userIndex, e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // Wait for all to complete
        boolean completed = endLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        log.info("CONFIRMED: {}, WAITLISTED: {}", confirmedCount.get(), waitlistedCount.get());

        // Verify capacity is respected
        assertThat(confirmedCount.get())
                .as("Should have at most {} CONFIRMED bookings", SLOT_CAPACITY)
                .isLessThanOrEqualTo(SLOT_CAPACITY);
        assertThat(confirmedCount.get())
                .as("Should have exactly {} CONFIRMED bookings", SLOT_CAPACITY)
                .isEqualTo(SLOT_CAPACITY);

        // Now cancel one CONFIRMED booking and verify a WAITLISTED user can be booked
        List<Booking> confirmedBookings = bookingRepository.findAll().stream()
                .filter(b -> b.getStatus() == BookingStatus.CONFIRMED && b.getTimeSlot().getId().equals(slot.getId()))
                .toList();

        assertThat(confirmedBookings).hasSize(SLOT_CAPACITY);

        // Cancel one booking
        Booking toCancel = confirmedBookings.get(0);
        bookingService.cancelBooking(toCancel.getId(), toCancel.getUser().getId());

        // Verify one slot is now freed
        long confirmedAfterCancel = bookingRepository.countByTimeSlotIdAndStatus(slot.getId(), BookingStatus.CONFIRMED);
        assertThat(confirmedAfterCancel)
                .as("After cancellation, should have {} CONFIRMED bookings", SLOT_CAPACITY - 1)
                .isEqualTo(SLOT_CAPACITY - 1);

        log.info("=== Capacity Protection Test PASSED ===");
    }

    @Test
    @DisplayName("Scenario 3: Concurrent bookings with stress - verify no double bookings")
    @Timeout(45)
    void testConcurrentStressNoDoubleBoohing() throws InterruptedException {
        log.info("=== Starting Concurrent Stress Test ===");

        // Create a slot with capacity 5
        slot.setCapacity(5);
        entityManager.merge(slot);
        entityManager.flush();

        ExecutorService executor = Executors.newFixedThreadPool(20);  // More threads than capacity
        CountDownLatch latch = new CountDownLatch(20);

        AtomicInteger confirmed = new AtomicInteger(0);
        AtomicInteger waitlisted = new AtomicInteger(0);
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < 20; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    User user = (idx < users.size()) ? users.get(idx) : users.get(idx % users.size());
                    CreateBookingRequest request = new CreateBookingRequest();
                    request.setSlotId(slot.getId());

                    BookingResponse response = bookingService.book(user.getId(), request);

                    if (response.getStatus() == BookingStatus.CONFIRMED) {
                        confirmed.incrementAndGet();
                    } else {
                        waitlisted.incrementAndGet();
                    }

                } catch (Exception e) {
                    synchronized (errors) {
                        errors.add("Thread " + idx + ": " + e.getMessage());
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(45, TimeUnit.SECONDS);
        executor.shutdown();

        log.info("Confirmed: {}, Waitlisted: {}, Errors: {}", confirmed.get(), waitlisted.get(), errors.size());

        // Verify no errors
        assertThat(errors).as("Should have no booking errors").isEmpty();

        // Verify confirmed bookings don't exceed capacity
        assertThat(confirmed.get())
                .as("CONFIRMED bookings should not exceed capacity of 5")
                .isLessThanOrEqualTo(5);

        // Verify total bookings = 20
        long total = bookingRepository.findBySlotId(slot.getId()).size();
        assertThat(total).as("Total bookings should be 20").isEqualTo(20);

        log.info("=== Concurrent Stress Test PASSED ===");
    }

    @Test
    @DisplayName("Scenario 4: Verify waitlist is used as fallback")
    @Timeout(30)
    void testWaitlistFallback() throws InterruptedException {
        log.info("=== Starting Waitlist Fallback Test ===");

        // Fill slot to capacity with confirmed bookings
        slot.setCapacity(2);
        entityManager.merge(slot);
        entityManager.flush();

        for (int i = 0; i < 2; i++) {
            User user = users.get(i);
            CreateBookingRequest request = new CreateBookingRequest();
            request.setSlotId(slot.getId());
            BookingResponse response = bookingService.book(user.getId(), request);
            log.info("Pre-filled slot with booking {} (status: {})", response.getId(), response.getStatus());
        }

        entityManager.clear();

        // Try booking when slot is full
        User user3 = users.get(2);
        CreateBookingRequest request = new CreateBookingRequest();
        request.setSlotId(slot.getId());

        BookingResponse response = bookingService.book(user3.getId(), request);

        log.info("Booking attempt on full slot: {} (status: {})", response.getId(), response.getStatus());

        // Verify booking was created but with WAITLISTED status
        assertThat(response.getStatus())
                .as("Booking should have WAITLISTED status")
                .isEqualTo(BookingStatus.WAITLISTED);

        // Verify database
        long waitlistedCount = bookingRepository.countByTimeSlotIdAndStatus(slot.getId(), BookingStatus.WAITLISTED);
        assertThat(waitlistedCount)
                .as("Should have 1 WAITLISTED booking")
                .isEqualTo(1);

        log.info("=== Waitlist Fallback Test PASSED ===");
    }

    @Test
    @DisplayName("Scenario 5: High contention with many threads - verify eventual consistency")
    @Timeout(60)
    void testHighContentionEventualConsistency() throws InterruptedException {
        log.info("=== Starting High Contention Test ===");

        slot.setCapacity(3);
        entityManager.merge(slot);
        entityManager.flush();

        int threadCount = 30;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        AtomicInteger confirmed = new AtomicInteger(0);
        AtomicInteger waitlisted = new AtomicInteger(0);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    User user = users.get(idx % users.size());
                    CreateBookingRequest request = new CreateBookingRequest();
                    request.setSlotId(slot.getId());

                    BookingResponse response = bookingService.book(user.getId(), request);

                    if (response.getStatus() == BookingStatus.CONFIRMED) {
                        confirmed.incrementAndGet();
                    } else if (response.getStatus() == BookingStatus.WAITLISTED) {
                        waitlisted.incrementAndGet();
                    }

                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        log.info("High contention test completed in {}ms", duration);
        log.info("CONFIRMED: {}, WAITLISTED: {}", confirmed.get(), waitlisted.get());

        // Verify results
        assertThat(confirmed.get())
                .as("Should have exactly 3 CONFIRMED bookings")
                .isEqualTo(3);
        
        long actualConfirmed = bookingRepository.countByTimeSlotIdAndStatus(slot.getId(), BookingStatus.CONFIRMED);
        assertThat(actualConfirmed)
                .as("Database should have exactly 3 CONFIRMED bookings")
                .isEqualTo(3);

        log.info("=== High Contention Test PASSED ===");
    }
}
