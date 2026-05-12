package com.web.labportalbackend.booking.repository;

import com.web.labportalbackend.booking.entity.WaitlistEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.QueryHint;

import java.util.List;
import java.util.Optional;

@Repository
public interface WaitlistRepository extends JpaRepository<WaitlistEntity, Long> {

    /**
     * Find the maximum position for a given time slot using pessimistic locking.
     * <p>
     * This query locks the row to prevent race conditions when multiple threads
     * simultaneously calculate the next position. The lock is held until the
     * transaction completes, ensuring position uniqueness.
     * <p>
     * Timeout: 3 seconds (matching BookingCoreService pattern)
     *
     * @param slotId the time slot ID
     * @return Optional containing max position, or empty if no entries exist for slot
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({
        @QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")
    })
    @Query("SELECT MAX(w.position) FROM WaitlistEntity w WHERE w.timeSlot.id = :slotId")
    Optional<Integer> findMaxPositionBySlotId(@Param("slotId") Long slotId);

    /**
     * Fetch all waitlist entries for a slot, ordered by position ascending.
     *
     * @param slotId the time slot ID
     * @return list of waitlist entries ordered by position
     */
    @Query("SELECT w FROM WaitlistEntity w WHERE w.timeSlot.id = :slotId AND w.active = true AND w.deleted = false ORDER BY w.position ASC")
    List<WaitlistEntity> findBySlotIdOrderByPosition(@Param("slotId") Long slotId);

    /**
     * Check if a user already has an entry in a slot's waitlist.
     *
     * @param userId the user ID
     * @param slotId the slot ID
     * @return true if user already in waitlist for this slot
     */
    @Query("SELECT COUNT(w) > 0 FROM WaitlistEntity w WHERE w.user.id = :userId AND w.timeSlot.id = :slotId AND w.active = true AND w.deleted = false")
    boolean existsUserInWaitlist(@Param("userId") Long userId, @Param("slotId") Long slotId);
}
