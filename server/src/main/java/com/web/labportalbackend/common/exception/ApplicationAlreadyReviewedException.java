package com.web.labportalbackend.common.exception;

/**
 * Exception raised when attempting to review an application that has already been reviewed.
 */
public class ApplicationAlreadyReviewedException extends RuntimeException {

    public ApplicationAlreadyReviewedException(Long applicationId) {
        super("Application " + applicationId + " has already been reviewed");
    }

    public ApplicationAlreadyReviewedException(String message) {
        super(message);
    }
}
