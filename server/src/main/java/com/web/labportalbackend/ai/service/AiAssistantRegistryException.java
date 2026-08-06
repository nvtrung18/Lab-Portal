package com.web.labportalbackend.ai.service;

public final class AiAssistantRegistryException extends RuntimeException {

    private final AiAssistantRegistryFailure failure;

    public AiAssistantRegistryException(AiAssistantRegistryFailure failure) {
        super(failure.name());
        this.failure = failure;
    }

    public AiAssistantRegistryFailure failure() {
        return failure;
    }
}
