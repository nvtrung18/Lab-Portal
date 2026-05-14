package com.web.labportalbackend.common.exception;

/**
 * Custom exception for application-related errors.
 */
public class ApplicationException extends RuntimeException {

    public ApplicationException(String message) {
        super(message);
    }

    public ApplicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
