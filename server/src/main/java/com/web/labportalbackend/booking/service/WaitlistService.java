package com.web.labportalbackend.booking.service;

import com.web.labportalbackend.booking.dto.response.WaitlistResponse;

import java.util.List;

/**
 * Service interface for waitlist operations.
 * <p>
 * Manages user positions in time slot waitlists when slots are at capacity.
 * Handles race-condition-safe position assignment via pessimistic locking.
 * Supports automatic promotion of waiting users when bookings are cancelled.
 */
public interface WaitlistService {

    /**
     * Add a user to a time slot's waitlist.
     * <p>
     * Position is calculated atomically:
     * 1. Query MAX(position) for slot using PESSIMISTIC_WRITE lock
     * 2. Calculate newPosition = MAX + 1 (or 1 if no entries)
     * 3. Create and save WaitlistEntity
     * 4. Return WaitlistResponse with position info
     * <p>
     * UNIQUE constraint on (slot_id, user_id) prevents duplicate entries at DB level.
     *
     * @param userId the user ID
     * @param slotId the time slot ID
     * @return WaitlistResponse with assigned position
     * @throws com.web.labportalbackend.common.exception.WaitlistDuplicateException if user already in waitlist
     * @throws EntityNotFoundException if user or slot not found
     */
    WaitlistResponse addToWaitlist(Long userId, Long slotId);

    /**
     * Get all waitlist entries for a time slot, ordered by position.
     *
     * @param slotId the time slot ID
     * @return list of WaitlistResponse ordered by position (lowest first)
     */
    List<WaitlistResponse> getWaitlistBySlot(Long slotId);

    /**
     * Promote the first user in a slot's waitlist to a confirmed booking.
     * <p>
     * FIFO Principle: Always promotes user with lowest position.
     * <p>
     * Process:
     * 1. Query first user with status=PENDING using PESSIMISTIC_WRITE lock
     * 2. Check if user already has CONFIRMED booking (data integrity check)
     * 3. If exists: Mark as PROMOTED and recursively try next user
     * 4. If valid: Create new CONFIRMED booking and mark as PROMOTED
     * 5. Return gracefully if no one to promote
     * <p>
     * All operations in single @Transactional block for atomicity.
     * Lock timeout: 3 seconds (prevents deadlock).
     *
     * @param slotId the time slot ID
     * @throws PessimisticLockingFailureException if lock timeout occurs
     */
    void promoteNext(Long slotId);
}
