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
}
