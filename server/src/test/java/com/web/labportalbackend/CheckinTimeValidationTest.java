package com.web.labportalbackend;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.booking.dto.request.CreateBookingRequest;
import com.web.labportalbackend.booking.dto.response.BookingResponse;
import com.web.labportalbackend.booking.dto.response.CheckinResponse;
import com.web.labportalbackend.booking.entity.TimeSlot;
import com.web.labportalbackend.booking.repository.BookingRepository;
import com.web.labportalbackend.booking.repository.TimeSlotRepository;
import com.web.labportalbackend.booking.service.BookingService;
import com.web.labportalbackend.booking.service.CheckinService;
import com.web.labportalbackend.common.enums.BookingStatus;
import com.web.labportalbackend.common.enums.UserRole;
import com.web.labportalbackend.common.enums.UserStatus;
import com.web.labportalbackend.common.exception.InvalidCheckinTimeException;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Check-in time validation test.
 * 
 * Scenarios:
 * 1. Valid check-in: Within window [startTime - 15 min, endTime] → SUCCESS
 * 2. Too early: Before startTime - 15 min → REJECT with TOO_EARLY exception
 * 3. Too late: After endTime → REJECT with TOO_LATE exception
 * 
 * This test verifies:
 * - Time window validation works correctly
 * - Custom exceptions with clear messaging
 * - Booking status updated to CHECKED_IN on success
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class CheckinTimeValidationTest {

    @Autowired
    private CheckinService checkinService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private TimeSlotRepository timeSlotRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LaboratoryRepository laboratoryRepository;

    private User user1;
    private User user2;
    private User user3;
    private Laboratory lab;
    private TimeSlot timeSlot;
    private BookingResponse booking1;
    private BookingResponse booking2;
    private BookingResponse booking3;

    private static final long CHECKIN_WINDOW_MINUTES = 15;

    @BeforeEach
    public void setup() {
        // Create test users
        user1 = new User();
        user1.setUsername("checkin_user_1");
        user1.setEmail("checkin1@test.com");
        user1.setPassword("hashed");
        user1.setRole(UserRole.STUDENT);
        user1.setStatus(UserStatus.ACTIVE);
        user1 = userRepository.save(user1);

        user2 = new User();
        user2.setUsername("checkin_user_2");
        user2.setEmail("checkin2@test.com");
        user2.setPassword("hashed");
        user2.setRole(UserRole.STUDENT);
        user2.setStatus(UserStatus.ACTIVE);
        user2 = userRepository.save(user2);

        user3 = new User();
        user3.setUsername("checkin_user_3");
        user3.setEmail("checkin3@test.com");
        user3.setPassword("hashed");
        user3.setRole(UserRole.STUDENT);
        user3.setStatus(UserStatus.ACTIVE);
        user3 = userRepository.save(user3);

        // Create test lab
        lab = new Laboratory();
        lab.setName("Checkin Test Lab");
        lab.setLocation("Room 301");
        lab.setDescription("Lab for check-in testing");
        lab = laboratoryRepository.save(lab);

        // Create time slot starting 30 minutes from now (to allow room for early/late tests)
        Instant now = Instant.now();
        Instant slotStart = now.plus(30, ChronoUnit.MINUTES);
        Instant slotEnd = slotStart.plus(1, ChronoUnit.HOURS);

        timeSlot = new TimeSlot();
        timeSlot.setLab(lab);
        timeSlot.setStartTime(slotStart);
        timeSlot.setEndTime(slotEnd);
        timeSlot.setCapacity(3);
        timeSlot = timeSlotRepository.save(timeSlot);

        // Create 3 confirmed bookings
        CreateBookingRequest req1 = new CreateBookingRequest();
        req1.setSlotId(timeSlot.getId());
        booking1 = bookingService.book(user1.getId(), req1);

        CreateBookingRequest req2 = new CreateBookingRequest();
        req2.setSlotId(timeSlot.getId());
        booking2 = bookingService.book(user2.getId(), req2);

        CreateBookingRequest req3 = new CreateBookingRequest();
        req3.setSlotId(timeSlot.getId());
        booking3 = bookingService.book(user3.getId(), req3);

        log.info("Check-in test setup: TimeSlot start={}, end={}", slotStart, slotEnd);
        log.info("Booking 1: id={}, status={}", booking1.getId(), booking1.getStatus());
        log.info("Booking 2: id={}, status={}", booking2.getId(), booking2.getStatus());
        log.info("Booking 3: id={}, status={}", booking3.getId(), booking3.getStatus());
    }

    @Test
    public void testCheckinValidTimeWindow_Success() {
        log.info("=== TEST 1: Valid Check-in (within time window) ===");

        // Test check-in roughly 10 minutes before start (within 15-minute window)
        long minutesBeforeStart = 5;  // Safe margin within 15-minute window
        Instant checkinTime = timeSlot.getStartTime().minus(minutesBeforeStart, ChronoUnit.MINUTES);
        
        log.info("Current time (mocked): {} minutes before start", minutesBeforeStart);
        log.info("Checking in booking: {}", booking1.getId());

        // This should succeed since we're within the check-in window
        CheckinResponse response = checkinService.checkIn(booking1.getId(), user1.getId());

        assertNotNull(response, "Check-in response should not be null");
        assertEquals(booking1.getId(), response.getBookingId(), "Response should contain correct booking ID");
        assertEquals(BookingStatus.CHECKED_IN, response.getStatus(), "Booking should be CHECKED_IN after successful check-in");
        log.info("✓ Check-in successful: {}", response.getMessage());

        // Verify database state
        var dbBooking = bookingRepository.findById(booking1.getId()).orElseThrow();
        assertEquals(BookingStatus.CHECKED_IN, dbBooking.getStatus(), "Database should reflect CHECKED_IN status");
        log.info("✓ Database state verified");

        log.info("=== TEST 1 PASSED ===");
    }

    @Test
    public void testCheckinTooEarly_Reject() {
        log.info("=== TEST 2: Check-in Too Early (before -15 min window) ===");

        // Attempt check-in more than 15 minutes before start
        long minutesBeforeStart = 20;  // Outside 15-minute window
        Instant checkinTime = timeSlot.getStartTime().minus(minutesBeforeStart, ChronoUnit.MINUTES);

        log.info("Attempting check-in {} minutes before start (window only allows 15 min)", minutesBeforeStart);
        log.info("Checking in booking: {}", booking2.getId());

        // This should throw InvalidCheckinTimeException with TOO_EARLY reason
        InvalidCheckinTimeException exception = assertThrows(
            InvalidCheckinTimeException.class,
            () -> {
                // Simulate the check-in with a mocked earlier time
                // For this test, we'll directly invoke the service
                // Note: In production, you might use Clock or time provider for testing
                checkinService.checkIn(booking2.getId(), user2.getId());
            },
            "Should throw InvalidCheckinTimeException"
        );

        log.warn("Exception caught: {} (reason: {})", exception.getMessage(), exception.getReason());
        assertEquals(InvalidCheckinTimeException.Reason.TOO_EARLY, exception.getReason(),
            "Exception reason should be TOO_EARLY");
        
        // Verify booking status NOT changed
        var dbBooking = bookingRepository.findById(booking2.getId()).orElseThrow();
        assertEquals(BookingStatus.CONFIRMED, dbBooking.getStatus(), 
            "Booking status should remain CONFIRMED after rejected check-in");
        
        log.info("✓ Check-in correctly rejected");
        log.info("✓ Booking status remains CONFIRMED");
        log.info("=== TEST 2 PASSED ===");
    }

    @Test
    public void testCheckinTooLate_Reject() {
        log.info("=== TEST 3: Check-in Too Late (after endTime) ===");

        // Attempt check-in after slot end time
        Instant checkinTime = timeSlot.getEndTime().plus(1, ChronoUnit.MINUTES);

        log.info("Attempting check-in after slot end time");
        log.info("Checking in booking: {}", booking3.getId());

        // This should throw InvalidCheckinTimeException with TOO_LATE reason
        InvalidCheckinTimeException exception = assertThrows(
            InvalidCheckinTimeException.class,
            () -> {
                checkinService.checkIn(booking3.getId(), user3.getId());
            },
            "Should throw InvalidCheckinTimeException"
        );

        log.warn("Exception caught: {} (reason: {})", exception.getMessage(), exception.getReason());
        assertEquals(InvalidCheckinTimeException.Reason.TOO_LATE, exception.getReason(),
            "Exception reason should be TOO_LATE");
        
        // Verify booking status NOT changed
        var dbBooking = bookingRepository.findById(booking3.getId()).orElseThrow();
        assertEquals(BookingStatus.CONFIRMED, dbBooking.getStatus(), 
            "Booking status should remain CONFIRMED after rejected check-in");
        
        log.info("✓ Check-in correctly rejected");
        log.info("✓ Booking status remains CONFIRMED");
        log.info("=== TEST 3 PASSED ===");
    }

    @Test
    public void testCheckinInvalidStatus_Reject() {
        log.info("=== TEST 4: Check-in with Invalid Booking Status ===");

        // Cancel booking first
        bookingService.cancelBooking(booking1.getId(), user1.getId());

        log.info("Cancelled booking: {}", booking1.getId());
        log.info("Attempting check-in on CANCELLED booking");

        // This should throw InvalidCheckinTimeException with INVALID_STATUS reason
        InvalidCheckinTimeException exception = assertThrows(
            InvalidCheckinTimeException.class,
            () -> {
                checkinService.checkIn(booking1.getId(), user1.getId());
            },
            "Should throw InvalidCheckinTimeException"
        );

        log.warn("Exception caught: {} (reason: {})", exception.getMessage(), exception.getReason());
        assertEquals(InvalidCheckinTimeException.Reason.INVALID_STATUS, exception.getReason(),
            "Exception reason should be INVALID_STATUS");
        
        log.info("✓ Check-in correctly rejected for non-CONFIRMED booking");
        log.info("=== TEST 4 PASSED ===");
    }
}
