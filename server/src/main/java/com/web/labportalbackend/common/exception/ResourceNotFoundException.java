package com.web.labportalbackend.common.exception;

/**
 * Exception raised when a requested resource is not found.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resourceName, Long id) {
        super(resourceName + " not found with ID: " + id);
    }

    public ResourceNotFoundException(String resourceName, String fieldName, Object value) {
        super(resourceName + " not found with " + fieldName + ": " + value);
    }
}
