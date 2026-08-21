package com.web.labportalbackend.ai.service;

import java.util.Objects;

public final class AiAssistantAvailabilityException extends RuntimeException {

    private final AiAssistantAvailabilityFailure failure;

    public AiAssistantAvailabilityException(AiAssistantAvailabilityFailure failure) {
        this(failure, null);
    }

    public AiAssistantAvailabilityException(AiAssistantAvailabilityFailure failure, Throwable cause) {
        super(Objects.requireNonNull(failure, "failure is required").name(), cause);
        this.failure = failure;
    }

    public AiAssistantAvailabilityFailure failure() {
        return failure;
    }
}
