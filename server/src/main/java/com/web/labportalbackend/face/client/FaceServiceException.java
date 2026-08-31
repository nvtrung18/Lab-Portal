package com.web.labportalbackend.face.client;

public class FaceServiceException extends RuntimeException {
    private final boolean retryable;

    public FaceServiceException(String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean retryable() {
        return retryable;
    }
}
