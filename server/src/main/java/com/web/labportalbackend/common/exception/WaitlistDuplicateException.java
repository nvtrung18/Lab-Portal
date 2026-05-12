package com.web.labportalbackend.common.exception;

/**
 * Exception thrown when attempting to add a user to a waitlist they already exist in.
 * A duplicate waitlist entry occurs when a user attempts to join a waitlist for a slot
 * they are already waiting for.
 */
public class WaitlistDuplicateException extends RuntimeException {

    private final Long userId;
    private final Long slotId;

    public WaitlistDuplicateException(Long userId, Long slotId) {
        super(String.format("User %d is already in the waitlist for slot %d.", userId, slotId));
        this.userId = userId;
        this.slotId = slotId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getSlotId() {
        return slotId;
    }
}
