package com.web.labportalbackend.booking.service.impl;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.booking.dto.response.WaitlistResponse;
import com.web.labportalbackend.booking.entity.TimeSlot;
import com.web.labportalbackend.booking.entity.WaitlistEntity;
import com.web.labportalbackend.booking.repository.TimeSlotRepository;
import com.web.labportalbackend.booking.repository.WaitlistRepository;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrency tests for WaitlistServiceImpl position assignment.
 * <p>
 * Simulates 5 threads concurrently adding users to the same slot's waitlist.
 * Verifies that:
 * - All 5 records are created
 * - Positions are unique and sequential (1-5)
 * - No race conditions or duplicates occur
 * - UNIQUE constraint (slot_id, user_id) prevents duplicates at DB level
 * <p>
 * This test uses real database transactions (no mocks) to ensure
 * pessimistic locking and database constraints are properly tested.
 */
@DisplayName("WaitlistServiceImpl Concurrency Tests")
@DataJpaTest
@Import(WaitlistServiceImpl.class)
@ActiveProfiles("test")
class WaitlistConcurrencyTest {

    @Autowired
    private WaitlistRepository waitlistRepository;

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WaitlistServiceImpl waitlistService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private TimeSlot testSlot;
    private List<User> testUsers = new ArrayList<>();
    private static final int CONCURRENT_THREADS = 5;

    @BeforeEach
    void setUp() {
        // Create a test laboratory
        Laboratory testLab = new Laboratory();
        testLab.setLabName("Test Lab");
        testLab.setDescription("Lab for concurrency testing");
        testLab.setLocation("Building A");
        testLab.setCapacity(10);
        testLab = transactionTemplate.execute(status -> {
            entityManager.persist(testLab);
            entityManager.flush();
            return testLab;
        });

        // Create a test time slot
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

        // Create test users
        for (int i = 0; i < CONCURRENT_THREADS; i++) {
            User user = new User();
            user.setUsername("concurrency_test_user_" + i);
            user.setEmail("user" + i + "@test.com");
            user.setPassword("hashed_password");
            user = transactionTemplate.execute(status -> {
                entityManager.persist(user);
                entityManager.flush();
                return user;
            });
            testUsers.add(user);
        }
    }

    @Test
    @DisplayName("Concurrent addToWaitlist calls should create unique sequential positions")
    void testConcurrentWaitlistAdditions() throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(CONCURRENT_THREADS);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        List<Integer> collectedPositions = new ArrayList<>();

        // Submit 5 tasks that try to add to waitlist simultaneously
        for (int i = 0; i < CONCURRENT_THREADS; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready

                    WaitlistResponse response = waitlistService.addToWaitlist(
                            testUsers.get(index).getId(),
                            testSlot.getId()
                    );

                    synchronized (collectedPositions) {
                        collectedPositions.add(response.getPosition());
                    }
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // Release the latch to start all threads simultaneously
        startLatch.countDown();

        // Wait for all threads to complete
        endLatch.await();
        executorService.shutdown();

        // Verify results
        assertEquals(CONCURRENT_THREADS, successCount.get(), "All threads should succeed");
        assertEquals(0, errorCount.get(), "No threads should error");

        // Verify positions are unique and sequential
        assertEquals(CONCURRENT_THREADS, collectedPositions.size(), "Should have 5 positions");
        Set<Integer> uniquePositions = new HashSet<>(collectedPositions);
        assertEquals(CONCURRENT_THREADS, uniquePositions.size(), "All positions should be unique");

        // Verify positions are in range [1, 5]
        for (Integer pos : collectedPositions) {
            assertTrue(pos >= 1 && pos <= CONCURRENT_THREADS, 
                    "Position " + pos + " should be between 1 and " + CONCURRENT_THREADS);
        }

        // Verify database state
        List<WaitlistEntity> dbEntries = waitlistRepository.findBySlotIdOrderByPosition(testSlot.getId());
        assertEquals(CONCURRENT_THREADS, dbEntries.size(), "Database should have 5 waitlist entries");

        // Verify positions in DB are sequential
        for (int i = 0; i < CONCURRENT_THREADS; i++) {
            assertEquals(i + 1, dbEntries.get(i).getPosition(), 
                    "Position " + (i + 1) + " should exist at index " + i);
        }
    }

    @Test
    @DisplayName("Duplicate user in waitlist should fail with exception during concurrency")
    void testDuplicateUserDetectionUnderConcurrency() throws InterruptedException {
        // First, add a user to waitlist successfully
        transactionTemplate.execute(status -> {
            waitlistService.addToWaitlist(testUsers.get(0).getId(), testSlot.getId());
            return null;
        });

        // Now try to add the same user multiple times concurrently
        ExecutorService executorService = Executors.newFixedThreadPool(3);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(3);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger duplicateErrorCount = new AtomicInteger(0);

        for (int i = 0; i < 3; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    waitlistService.addToWaitlist(testUsers.get(0).getId(), testSlot.getId());
                    successCount.incrementAndGet();
                } catch (com.web.labportalbackend.common.exception.WaitlistDuplicateException e) {
                    duplicateErrorCount.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await();
        executorService.shutdown();

        // Exactly 1 should succeed (the first attempt), and 2 should fail with duplicate error
        assertEquals(0, successCount.get(), "No additional success (already in waitlist)");
        assertEquals(3, duplicateErrorCount.get(), "All 3 retry attempts should fail with duplicate error");

        // Verify database still has only 1 entry for this user-slot combination
        long count = waitlistRepository.findBySlotIdOrderByPosition(testSlot.getId()).stream()
                .filter(w -> w.getUser().getId().equals(testUsers.get(0).getId()))
                .count();
        assertEquals(1, count, "Should have exactly 1 entry for this user-slot combination");
    }
}
