package com.web.labportalbackend.booking.service.impl;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.booking.dto.request.CreateBookingRequest;
import com.web.labportalbackend.booking.dto.response.BookingResponse;
import com.web.labportalbackend.booking.dto.response.WaitlistResponse;
import com.web.labportalbackend.booking.entity.Booking;
import com.web.labportalbackend.booking.entity.TimeSlot;
import com.web.labportalbackend.booking.repository.BookingRepository;
import com.web.labportalbackend.booking.repository.TimeSlotRepository;
import com.web.labportalbackend.booking.repository.WaitlistRepository;
import com.web.labportalbackend.booking.service.BookingService;
import com.web.labportalbackend.booking.service.WaitlistService;
import com.web.labportalbackend.common.enums.BookingStatus;
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
 * Integration tests for BookingService with Waitlist fallback logic.
 * <p>
 * Tests the happy path:
 * 1. User books slot successfully when capacity available
 * 2. Slot fills up (reaches capacity)
 * 3. Next user attempting to book falls back to waitlist
 * 4. Booking record created with WAITLISTED status
 * 5. WaitlistEntity created with correct position
 * <p>
 * Ensures BookingService properly delegates to WaitlistService when slot full.
 */
@DisplayName("BookingService + Waitlist Integration Tests")
@DataJpaTest
@Import({BookingServiceImpl.class, BookingCoreService.class, WaitlistServiceImpl.class})
@ActiveProfiles("test")
class BookingServiceWaitlistIntegrationTest {

    @Autowired
    private BookingService bookingService;

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

    @BeforeEach
    void setUp() {
        // Create test laboratory
        testLab = new Laboratory();
        testLab.setLabName("Integration Test Lab");
        testLab.setDescription("Lab for booking-waitlist integration testing");
        testLab.setLocation("Building C");
        testLab.setCapacity(10);
        testLab = transactionTemplate.execute(status -> {
            entityManager.persist(testLab);
            entityManager.flush();
            return testLab;
        });

        // Create time slot with capacity = 2 (for easy testing)
        testSlot = TimeSlot.builder()
                .lab(testLab)
                .startTime(Instant.now())
                .endTime(Instant.now().plusSeconds(3600))
                .capacity(2)  // Small capacity to test overflow
                .build();
        testSlot = transactionTemplate.execute(status -> {
            entityManager.persist(testSlot);
            entityManager.flush();
            return testSlot;
        });

        // Create test users
        testUsers = transactionTemplate.execute(status -> {
            List<User> users = new java.util.ArrayList<>();
            for (int i = 0; i < 4; i++) {
                User user = new User();
                user.setUsername("integration_test_user_" + i);
                user.setEmail("integration_user" + i + "@test.com");
                user.setPassword("hashed_password");
                entityManager.persist(user);
                users.add(user);
            }
            entityManager.flush();
            return users;
        });
    }

    @Test
    @DisplayName("First user books slot successfully with CONFIRMED status")
    void testFirstUserBooksSlot_Confirmed() {
        // Arrange
        User user1 = testUsers.get(0);
        CreateBookingRequest request = new CreateBookingRequest();
        request.setSlotId(testSlot.getId());

        // Act
        BookingResponse response = bookingService.book(user1.getId(), request);

        // Assert
        assertNotNull(response);
        assertNotNull(response.getId());
        assertEquals(BookingStatus.CONFIRMED, response.getStatus());
        assertEquals(user1.getId(), response.getUserId());
        assertEquals(testSlot.getId(), response.getSlotId());

        // Verify in database
        List<Booking> bookings = bookingRepository.findBySlotId(testSlot.getId());
        assertEquals(1, bookings.size());
        assertEquals(BookingStatus.CONFIRMED, bookings.get(0).getStatus());
    }

    @Test
    @DisplayName("Second user books slot successfully with CONFIRMED status (slot has capacity)")
    void testSecondUserBooksSlot_Confirmed() {
        // Arrange
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);
        CreateBookingRequest request = new CreateBookingRequest();
        request.setSlotId(testSlot.getId());

        // Act: First user books
        bookingService.book(user1.getId(), request);

        // Act: Second user books (slot still has capacity)
        BookingResponse response = bookingService.book(user2.getId(), request);

        // Assert
        assertNotNull(response);
        assertEquals(BookingStatus.CONFIRMED, response.getStatus());
        assertEquals(user2.getId(), response.getUserId());

        // Verify both bookings in database with CONFIRMED status
        List<Booking> bookings = bookingRepository.findBySlotId(testSlot.getId());
        assertEquals(2, bookings.size());
        for (Booking booking : bookings) {
            assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
        }
    }

    @Test
    @DisplayName("Third user is placed in waitlist with position 1 when slot is full")
    void testThirdUserFallsBackToWaitlist_Position1() {
        // Arrange
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);
        User user3 = testUsers.get(2);
        CreateBookingRequest request = new CreateBookingRequest();
        request.setSlotId(testSlot.getId());

        // Act: Fill up the slot (capacity = 2)
        bookingService.book(user1.getId(), request);
        bookingService.book(user2.getId(), request);

        // Act: Third user tries to book (slot is full)
        BookingResponse response = bookingService.book(user3.getId(), request);

        // Assert: Response indicates waitlisted status
        assertNotNull(response);
        assertEquals(BookingStatus.WAITLISTED, response.getStatus());
        assertEquals(user3.getId(), response.getUserId());

        // Verify booking record exists with WAITLISTED status
        List<Booking> allBookings = bookingRepository.findBySlotId(testSlot.getId());
        assertEquals(3, allBookings.size());  // 2 CONFIRMED + 1 WAITLISTED

        Booking waitlistedBooking = allBookings.stream()
                .filter(b -> b.getUser().getId().equals(user3.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals(BookingStatus.WAITLISTED, waitlistedBooking.getStatus());

        // Verify waitlist entry exists with position 1
        List<WaitlistResponse> waitlist = waitlistRepository.findBySlotIdOrderByPosition(testSlot.getId())
                .stream()
                .map(w -> new WaitlistResponse(
                        w.getId(),
                        w.getTimeSlot().getId(),
                        w.getUser().getId(),
                        w.getPosition(),
                        w.getCreatedAt(),
                        w.getUpdatedAt()
                ))
                .toList();
        assertEquals(1, waitlist.size());
        assertEquals(1, waitlist.get(0).getPosition());
        assertEquals(user3.getId(), waitlist.get(0).getUserId());
    }

    @Test
    @DisplayName("Fourth user gets waitlist position 2 after third user")
    void testFourthUserWaitlist_Position2() {
        // Arrange
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);
        User user3 = testUsers.get(2);
        User user4 = testUsers.get(3);
        CreateBookingRequest request = new CreateBookingRequest();
        request.setSlotId(testSlot.getId());

        // Act: Fill slot + add 2 to waitlist
        bookingService.book(user1.getId(), request);
        bookingService.book(user2.getId(), request);
        bookingService.book(user3.getId(), request);  // Position 1 in waitlist
        BookingResponse response = bookingService.book(user4.getId(), request);  // Position 2 in waitlist

        // Assert
        assertEquals(BookingStatus.WAITLISTED, response.getStatus());

        // Verify waitlist positions
        List<WaitlistResponse> waitlist = waitlistRepository.findBySlotIdOrderByPosition(testSlot.getId())
                .stream()
                .map(w -> new WaitlistResponse(
                        w.getId(),
                        w.getTimeSlot().getId(),
                        w.getUser().getId(),
                        w.getPosition(),
                        w.getCreatedAt(),
                        w.getUpdatedAt()
                ))
                .toList();
        assertEquals(2, waitlist.size());
        assertEquals(1, waitlist.get(0).getPosition());
        assertEquals(user3.getId(), waitlist.get(0).getUserId());
        assertEquals(2, waitlist.get(1).getPosition());
        assertEquals(user4.getId(), waitlist.get(1).getUserId());
    }

    @Test
    @DisplayName("Confirmed bookings are correct count (capacity not exceeded)")
    void testConfirmedBookingsNotExceedCapacity() {
        // Arrange
        User user1 = testUsers.get(0);
        User user2 = testUsers.get(1);
        User user3 = testUsers.get(2);
        CreateBookingRequest request = new CreateBookingRequest();
        request.setSlotId(testSlot.getId());

        // Act: Book 3 users (capacity = 2)
        bookingService.book(user1.getId(), request);
        bookingService.book(user2.getId(), request);
        bookingService.book(user3.getId(), request);

        // Assert: Only 2 confirmed, 1 waitlisted
        long confirmedCount = bookingRepository.countByTimeSlotIdAndStatus(testSlot.getId(), BookingStatus.CONFIRMED);
        long waitlistedCount = bookingRepository.findBySlotId(testSlot.getId())
                .stream()
                .filter(b -> b.getStatus() == BookingStatus.WAITLISTED)
                .count();

        assertEquals(2, confirmedCount, "Confirmed bookings should not exceed capacity");
        assertEquals(1, waitlistedCount, "One booking should be waitlisted");
    }

    @Test
    @DisplayName("Waitlist sequence remains correct: 1, 2, 3, ... (no gaps or duplicates)")
    void testWaitlistSequenceIntegrity() {
        // This test ensures the booking service integrates correctly with waitlist
        // for multiple sequential additions
        CreateBookingRequest request = new CreateBookingRequest();
        request.setSlotId(testSlot.getId());

        // Book users 0 and 1 (fills slot capacity=2)
        bookingService.book(testUsers.get(0).getId(), request);
        bookingService.book(testUsers.get(1).getId(), request);

        // Create a 5th user for this specific test
        User user5 = transactionTemplate.execute(status -> {
            User u = new User();
            u.setUsername("extra_user");
            u.setEmail("extra@test.com");
            u.setPassword("pwd");
            entityManager.persist(u);
            entityManager.flush();
            return u;
        });

        // Add users 2, 3, 4, 5 to waitlist
        bookingService.book(testUsers.get(2).getId(), request);
        bookingService.book(testUsers.get(3).getId(), request);
        bookingService.book(user5.getId(), request);

        // Verify waitlist positions
        List<WaitlistResponse> waitlist = waitlistRepository.findBySlotIdOrderByPosition(testSlot.getId())
                .stream()
                .map(w -> new WaitlistResponse(
                        w.getId(),
                        w.getTimeSlot().getId(),
                        w.getUser().getId(),
                        w.getPosition(),
                        w.getCreatedAt(),
                        w.getUpdatedAt()
                ))
                .toList();

        assertEquals(3, waitlist.size(), "Should have 3 waitlist entries");
        for (int i = 0; i < 3; i++) {
            assertEquals(i + 1, waitlist.get(i).getPosition(), 
                    "Position " + (i + 1) + " must exist at index " + i);
        }
    }
}
