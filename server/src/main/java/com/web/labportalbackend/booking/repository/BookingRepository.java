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
}
