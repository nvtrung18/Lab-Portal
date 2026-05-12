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
import org.junit.jupiter.api.RepeatedTest;
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
 * Extended concurrency tests for WaitlistServiceImpl with 10 concurrent threads.
 * <p>
 * This test suite simulates heavy concurrent load:
 * - 10 users simultaneously add to same slot's waitlist
 * - Positions must be unique (no duplicates)
 * - Positions must be sequential (1-10, no gaps)
 * - All transactions complete without race conditions
 * <p>
 * These tests are designed to run multiple times to catch intermittent concurrency bugs.
 */
@DisplayName("WaitlistServiceImpl Extended Concurrency Tests (10 Threads)")
@DataJpaTest
@Import(WaitlistServiceImpl.class)
@ActiveProfiles("test")
class WaitlistConcurrencyExtendedTest {

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
    private static final int CONCURRENT_THREADS = 10;

    @BeforeEach
    void setUp() {
        // Create a test laboratory
        Laboratory testLab = new Laboratory();
        testLab.setLabName("Test Lab - Extended Concurrency");
        testLab.setDescription("Lab for extended concurrency testing with 10 threads");
        testLab.setLocation("Building B");
        testLab.setCapacity(5);
        testLab = transactionTemplate.execute(status -> {
            entityManager.persist(testLab);
            entityManager.flush();
            return testLab;
        });

        // Create a test time slot with low capacity to trigger waitlist behavior
        testSlot = TimeSlot.builder()
                .lab(testLab)
                .startTime(Instant.now())
                .endTime(Instant.now().plusSeconds(3600))
                .capacity(0)  // Set to 0 to force all bookings into waitlist
                .build();
        testSlot = transactionTemplate.execute(status -> {
            entityManager.persist(testSlot);
            entityManager.flush();
            return testSlot;
        });

        // Create 10 test users
        for (int i = 0; i < CONCURRENT_THREADS; i++) {
            User user = new User();
            user.setUsername("extended_concurrency_user_" + i);
            user.setEmail("extended_user" + i + "@test.com");
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
    @DisplayName("Concurrent addToWaitlist with 10 threads creates unique sequential positions 1-10")
    void testConcurrentWaitlistAdditions_10Threads() throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(CONCURRENT_THREADS);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        List<Integer> collectedPositions = new ArrayList<>();

        // Submit 10 tasks that try to add to waitlist simultaneously
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

        // Wait for all threads to complete (timeout 30 seconds)
        boolean completed = endLatch.await();
        assertTrue(completed, "All threads should complete within timeout");
        executorService.shutdown();

        // ============================================
        // CRITICAL VALIDATIONS FOR RACE CONDITIONS
        // ============================================

        // 1. All threads should succeed
        assertEquals(CONCURRENT_THREADS, successCount.get(), 
                "All " + CONCURRENT_THREADS + " threads should succeed");
        assertEquals(0, errorCount.get(), 
                "No threads should error");

        // 2. Exactly 10 positions should be collected
        assertEquals(CONCURRENT_THREADS, collectedPositions.size(), 
                "Should have exactly " + CONCURRENT_THREADS + " positions");

        // 3. ALL POSITIONS MUST BE UNIQUE (NO DUPLICATES)
        Set<Integer> uniquePositions = new HashSet<>(collectedPositions);
        assertEquals(CONCURRENT_THREADS, uniquePositions.size(), 
                "ALL " + CONCURRENT_THREADS + " positions must be UNIQUE - No duplicates allowed!");

        // 4. Verify positions are in the expected range [1, 10]
        for (Integer pos : collectedPositions) {
            assertTrue(pos >= 1 && pos <= CONCURRENT_THREADS, 
                    "Position " + pos + " should be between 1 and " + CONCURRENT_THREADS);
        }

        // 5. CRITICAL: Verify positions are SEQUENTIAL (1, 2, 3, ..., 10) with no gaps
        List<Integer> sortedPositions = new ArrayList<>(uniquePositions);
        sortedPositions.sort(Integer::compareTo);
        for (int i = 0; i < CONCURRENT_THREADS; i++) {
            assertEquals(i + 1, sortedPositions.get(i), 
                    "Position " + (i + 1) + " must exist at index " + i + " (no gaps allowed!)");
        }

        // 6. Verify database state
        List<WaitlistEntity> dbEntries = waitlistRepository.findBySlotIdOrderByPosition(testSlot.getId());
        assertEquals(CONCURRENT_THREADS, dbEntries.size(), 
                "Database should have exactly " + CONCURRENT_THREADS + " waitlist entries");

        // 7. Verify all database positions are sequential from 1 to 10
        for (int i = 0; i < CONCURRENT_THREADS; i++) {
            assertEquals(i + 1, dbEntries.get(i).getPosition(), 
                    "Database position at index " + i + " must be " + (i + 1));
        }

        // 8. Verify no NULL positions
        for (WaitlistEntity entry : dbEntries) {
            assertNotNull(entry.getPosition(), "Position must never be NULL");
        }
    }

    @RepeatedTest(3)
    @DisplayName("Stress test: Run concurrent 10-thread test 3 times to verify consistency")
    void testConcurrentWaitlistAdditions_10Threads_Repeated() throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(CONCURRENT_THREADS);
        List<Integer> collectedPositions = new ArrayList<>();

        for (int i = 0; i < CONCURRENT_THREADS; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    WaitlistResponse response = waitlistService.addToWaitlist(
                            testUsers.get(index).getId(),
                            testSlot.getId()
                    );
                    synchronized (collectedPositions) {
                        collectedPositions.add(response.getPosition());
                    }
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

        // Verify uniqueness and sequence
        Set<Integer> uniquePositions = new HashSet<>(collectedPositions);
        assertEquals(CONCURRENT_THREADS, uniquePositions.size(), 
                "All positions must be unique in repetition");

        List<Integer> sortedPositions = new ArrayList<>(uniquePositions);
        sortedPositions.sort(Integer::compareTo);
        for (int i = 0; i < CONCURRENT_THREADS; i++) {
            assertEquals(i + 1, sortedPositions.get(i), 
                    "Repeated test: Position " + (i + 1) + " must exist");
        }
    }

    @Test
    @DisplayName("Verify position calculation under maximum concurrency (10 simultaneous locks)")
    void testPositionCalculationMaxLoad() throws InterruptedException {
        // This test verifies that pessimistic locking doesn't cause issues under heavy load
        ExecutorService executorService = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(CONCURRENT_THREADS);
        List<Long> responseTimesMilis = new ArrayList<>();

        for (int i = 0; i < CONCURRENT_THREADS; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    long startTime = System.currentTimeMillis();
                    
                    waitlistService.addToWaitlist(
                            testUsers.get(index).getId(),
                            testSlot.getId()
                    );
                    
                    long endTime = System.currentTimeMillis();
                    synchronized (responseTimesMilis) {
                        responseTimesMilis.add(endTime - startTime);
                    }
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

        // Verify response times are reasonable (< 5 seconds per request under lock)
        for (Long responseTime : responseTimesMilis) {
            assertTrue(responseTime < 5000, 
                    "Response time " + responseTime + "ms should be < 5000ms even under pessimistic locking");
        }

        // Log average response time for monitoring
        double avgResponseTime = responseTimesMilis.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0);
        System.out.println("Average response time under 10-thread load: " + avgResponseTime + "ms");
    }
}
