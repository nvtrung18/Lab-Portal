package com.web.labportalbackend.common.exception;

/**
 * Exception raised when attempting to create a duplicate application.
 * A user cannot apply to the same laboratory twice.
 */
public class DuplicateApplicationException extends ApplicationException {

    public DuplicateApplicationException(Long userId, Long labId) {
        super(String.format("User %d has already applied to laboratory %d", userId, labId));
    }

    public DuplicateApplicationException(String message) {
        super(message);
    }
}
