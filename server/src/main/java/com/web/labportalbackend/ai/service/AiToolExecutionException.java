package com.web.labportalbackend.ai.service;

public final class AiToolExecutionException extends RuntimeException {

    private final AiToolExecutionFailure failure;
    private final String requestId;

    public AiToolExecutionException(AiToolExecutionFailure failure, String requestId) {
        super(message(failure));
        if (failure == null) {
            throw new IllegalArgumentException("tool execution failure is required");
        }
        this.failure = failure;
        this.requestId = requestId;
    }

    public AiToolExecutionFailure failure() {
        return failure;
    }

    public String requestId() {
        return requestId;
    }

    private static String message(AiToolExecutionFailure failure) {
        if (failure == null) {
            return "Tool execution denied";
        }
        return switch (failure) {
            case UNKNOWN_TOOL -> "Unknown AI tool";
            case TOOL_NOT_ALLOWED -> "AI tool is not allowed";
            case INVALID_TOOL_ARGUMENTS -> "AI tool arguments are invalid";
            case RESOURCE_NOT_AUTHORIZED -> "AI tool resource is not authorized";
            case TOOL_GATE_REQUIRED -> "AI tool action gate is required";
            case TOOL_CONFIRMATION_REQUIRED -> "AI tool action requires user confirmation";
            case TOOL_APPROVAL_REQUIRED -> "AI tool action requires authorized approval";
            case TOOL_EXECUTION_FAILED -> "Tool execution failed";
        };
    }
}
