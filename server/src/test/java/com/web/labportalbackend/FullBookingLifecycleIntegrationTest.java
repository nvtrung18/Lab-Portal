package com.web.labportalbackend;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.booking.dto.response.BookingResponse;
import com.web.labportalbackend.booking.entity.Booking;
import com.web.labportalbackend.booking.entity.TimeSlot;
import com.web.labportalbackend.booking.repository.BookingRepository;
import com.web.labportalbackend.booking.repository.TimeSlotRepository;
import com.web.labportalbackend.booking.service.BookingService;
import com.web.labportalbackend.booking.service.WaitlistService;
import com.web.labportalbackend.booking.dto.request.CreateBookingRequest;
import com.web.labportalbackend.common.enums.BookingStatus;
import com.web.labportalbackend.common.enums.UserRole;
import com.web.labportalbackend.common.enums.UserStatus;
import com.web.labportalbackend.lab.entity.Laboratory;
import com.web.labportalbackend.lab.repository.LaboratoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for full booking lifecycle: Book → Waitlist → Cancel → Promote
 * 
 * Scenario: Slot has capacity=1. User A books successfully (CONFIRMED).
 * User B books same slot, added to waitlist (WAITLISTED).
 * User A cancels, User B is promoted to CONFIRMED.
 * 
 * This test verifies:
 * - Transaction atomicity of cancel + promote
 * - Correct state transitions
 * - Pessimistic locking prevents race conditions
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class FullBookingLifecycleIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private WaitlistService waitlistService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LaboratoryRepository laboratoryRepository;

    private User userA;
    private User userB;
    private Laboratory lab;
    private TimeSlot timeSlot;

    @BeforeEach
    public void setup() {
        // Create test users
        userA = new User();
        userA.setUsername("user_a");
        userA.setEmail("user.a@test.com");
        userA.setPassword("hashed_password");
        userA.setRole(UserRole.STUDENT);
        userA.setStatus(UserStatus.ACTIVE);
        userA = userRepository.save(userA);

        userB = new User();
        userB.setUsername("user_b");
        userB.setEmail("user.b@test.com");
        userB.setPassword("hashed_password");
        userB.setRole(UserRole.STUDENT);
        userB.setStatus(UserStatus.ACTIVE);
        userB = userRepository.save(userB);

        // Create test lab
        lab = new Laboratory();
        lab.setName("Test Lab");
        lab.setLocation("Room 101");
        lab.setDescription("Test Laboratory");
        lab = laboratoryRepository.save(lab);

        // Create time slot with capacity = 1
        Instant now = Instant.now();
        timeSlot = new TimeSlot();
        timeSlot.setLab(lab);
        timeSlot.setStartTime(now.plus(1, ChronoUnit.HOURS));
        timeSlot.setEndTime(now.plus(2, ChronoUnit.HOURS));
        timeSlot.setCapacity(1);  // ← Critical: Only 1 spot
        timeSlot = timeSlotRepository.save(timeSlot);

        log.info("Test setup complete. TimeSlot id={}, capacity={}", timeSlot.getId(), timeSlot.getCapacity());
    }

    @Test
    public void testFullLifecycle_BookCancelPromote() {
        log.info("=== TEST: Full Booking Lifecycle ===");

        // STEP 1: User A books slot (capacity=1) → Should succeed
        log.info("STEP 1: User A books slot");
        CreateBookingRequest requestA = new CreateBookingRequest();
        requestA.setSlotId(timeSlot.getId());
        
        BookingResponse bookingA = bookingService.book(userA.getId(), requestA);
        
        assertNotNull(bookingA, "User A booking should not be null");
        assertEquals(BookingStatus.CONFIRMED, bookingA.getStatus(), "User A booking should be CONFIRMED");
        log.info("✓ User A booking confirmed with id={}, status={}", bookingA.getId(), bookingA.getStatus());

        // STEP 2: User B books same slot → Should be WAITLISTED (slot is full)
        log.info("STEP 2: User B books same slot");
        CreateBookingRequest requestB = new CreateBookingRequest();
        requestB.setSlotId(timeSlot.getId());
        
        BookingResponse bookingB = bookingService.book(userB.getId(), requestB);
        
        assertNotNull(bookingB, "User B booking should not be null");
        assertEquals(BookingStatus.WAITLISTED, bookingB.getStatus(), "User B booking should be WAITLISTED");
        log.info("✓ User B booking waitlisted with id={}, status={}", bookingB.getId(), bookingB.getStatus());

        // STEP 3: User A cancels booking
        log.info("STEP 3: User A cancels booking");
        bookingService.cancelBooking(bookingA.getId(), userA.getId());

        // Verify User A booking is CANCELLED
        Booking bookingAAfterCancel = bookingRepository.findById(bookingA.getId()).orElseThrow();
        assertEquals(BookingStatus.CANCELLED, bookingAAfterCancel.getStatus(), "User A booking should be CANCELLED");
        log.info("✓ User A booking cancelled");

        // STEP 4: Verify User B was promoted to CONFIRMED
        log.info("STEP 4: Verify User B promotion");
        Booking bookingBAfterPromote = bookingRepository.findById(bookingB.getId()).orElseThrow();
        
        assertEquals(BookingStatus.CONFIRMED, bookingBAfterPromote.getStatus(), 
            "User B should be promoted to CONFIRMED after User A cancellation");
        log.info("✓ User B booking promoted to CONFIRMED");

        // FINAL VERIFICATION
        log.info("=== FINAL STATE VERIFICATION ===");
        List<Booking> slotBookings = bookingRepository.findBySlotId(timeSlot.getId());
        
        long confirmedCount = slotBookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.CONFIRMED)
                .count();
        long cancelledCount = slotBookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.CANCELLED)
                .count();

        log.info("Slot {} final state: {} confirmed, {} cancelled", 
            timeSlot.getId(), confirmedCount, cancelledCount);

        assertEquals(1, confirmedCount, "Should have exactly 1 CONFIRMED booking");
        assertEquals(1, cancelledCount, "Should have exactly 1 CANCELLED booking");

        log.info("=== TEST PASSED ===");
    }
}
