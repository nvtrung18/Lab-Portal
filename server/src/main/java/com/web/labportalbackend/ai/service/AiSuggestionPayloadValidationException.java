package com.web.labportalbackend.ai.service;

public final class AiSuggestionPayloadValidationException extends RuntimeException {

    public static final String MESSAGE = "Invalid AI suggestion payload.";

    public AiSuggestionPayloadValidationException() {
        super(MESSAGE);
    }
}
