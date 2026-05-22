package com.web.labportalbackend.booking.repository;

import com.web.labportalbackend.booking.entity.Booking;
import com.web.labportalbackend.common.enums.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByUserId(Long userId);

    List<Booking> findByLabId(Long labId);

    List<Booking> findByStatus(BookingStatus status);

    /**
     * Count bookings for a specific time slot.
     * Used for capacity validation.
     *
     * @param slotId the time slot ID
     * @return count of bookings for this slot
     */
    long countByTimeSlotId(Long slotId);

    /**
     * Count confirmed bookings for a specific time slot.
     * Used for accurate capacity validation - only counts CONFIRMED bookings.
     *
     * @param slotId the time slot ID
     * @param status the booking status (usually CONFIRMED)
     * @return count of confirmed bookings for this slot
     */
    long countByTimeSlotIdAndStatus(Long slotId, BookingStatus status);

    /**
     * Find overlapping bookings for a given lab in a specific time window.
     * Used for conflict detection when creating / updating a booking.
     */
    @Query("SELECT b FROM Booking b WHERE b.lab.id = :labId " +
           "AND b.status <> 'CANCELLED' " +
           "AND b.startTime < :endTime AND b.endTime > :startTime")
    List<Booking> findOverlappingBookings(
            @Param("labId") Long labId,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime
    );

    /**
     * Find bookings by time slot.
     *
     * @param slotId the time slot ID
     * @return list of bookings for the slot
     */
    @Query("SELECT b FROM Booking b WHERE b.timeSlot.id = :slotId AND b.deleted = false AND b.active = true")
    List<Booking> findBySlotId(@Param("slotId") Long slotId);

    @Query("SELECT b FROM Booking b WHERE b.user.id = :userId AND b.deleted = false AND b.active = true ORDER BY b.startTime DESC")
    List<Booking> findActiveByUserId(@Param("userId") Long userId);

    @Query("SELECT b FROM Booking b WHERE b.timeSlot.id = :slotId AND b.status IN :statuses AND b.deleted = false AND b.active = true")
    List<Booking> findBySlotIdAndStatusIn(
            @Param("slotId") Long slotId,
            @Param("statuses") List<BookingStatus> statuses
    );

    @Query("SELECT COUNT(b) FROM Booking b WHERE b.timeSlot.id = :slotId AND b.status = :status AND b.deleted = false AND b.active = true")
    long countActiveByTimeSlotIdAndStatus(@Param("slotId") Long slotId, @Param("status") BookingStatus status);

    /**
     * Check if a user has an existing (non-cancelled) booking for a slot.
     * Used for duplicate booking prevention.
     *
     * @param userId the user ID
     * @param slotId the slot ID
     * @return true if user already has a booking for this slot
     */
    @Query("SELECT COUNT(b) > 0 FROM Booking b WHERE b.user.id = :userId AND b.timeSlot.id = :slotId " +
           "AND b.status NOT IN ('CANCELLED', 'CANCELLED_BY_STUDENT', 'CANCELLED_BY_MANAGER', 'REJECTED') AND b.deleted = false AND b.active = true")
    boolean existsActiveBookingByUserAndSlot(@Param("userId") Long userId, @Param("slotId") Long slotId);

    @Query("SELECT b FROM Booking b WHERE b.status = :status " +
           "AND b.endTime <= :cutoff " +
           "AND b.deleted = false AND b.active = true")
    List<Booking> findNoShowCandidates(
            @Param("status") BookingStatus status,
            @Param("cutoff") Instant cutoff
    );

    @Query("SELECT b FROM Booking b JOIN FETCH b.user JOIN FETCH b.lab JOIN FETCH b.timeSlot ts " +
           "WHERE b.status = 'APPROVED' " +
           "AND ts.status <> 'CANCELLED' " +
           "AND b.startTime >= :fromTime AND b.startTime <= :toTime " +
           "AND b.deleted = false AND b.active = true")
    List<Booking> findApprovedBookingsOpeningCheckinWindow(
            @Param("fromTime") Instant fromTime,
            @Param("toTime") Instant toTime
    );
}
