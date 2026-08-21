package com.web.labportalbackend.ai.service;

/** Bounded audit-safe failure taxonomy. Raw exception details must never be persisted. */
public enum AiAuditFailureCode {
    INVALID_ASSISTANT_REQUEST,
    UNAUTHENTICATED,
    ACTOR_UNAVAILABLE,
    ASSISTANT_UNAVAILABLE,
    ROLE_NOT_ALLOWED,
    CONFIGURATION_UNAVAILABLE,
    QUOTA_EXCEEDED,
    RESOURCE_NOT_AUTHORIZED,
    GATEWAY_FAILED,
    INTERNAL_FAILURE,
    UNKNOWN_TOOL,
    TOOL_NOT_ALLOWED,
    INVALID_TOOL_ARGUMENTS,
    TOOL_GATE_REQUIRED,
    TOOL_CONFIRMATION_REQUIRED,
    TOOL_APPROVAL_REQUIRED,
    TOOL_EXECUTION_FAILED;

    public static AiAuditFailureCode from(AiAssistantAvailabilityFailure failure) {
        return switch (failure) {
            case UNAUTHENTICATED -> UNAUTHENTICATED;
            case ACTOR_UNAVAILABLE -> ACTOR_UNAVAILABLE;
            case ASSISTANT_UNAVAILABLE -> ASSISTANT_UNAVAILABLE;
            case ROLE_NOT_ALLOWED -> ROLE_NOT_ALLOWED;
            case CONFIGURATION_UNAVAILABLE -> CONFIGURATION_UNAVAILABLE;
            case QUOTA_EXCEEDED -> QUOTA_EXCEEDED;
        };
    }

    public static AiAuditFailureCode from(AiToolExecutionFailure failure) {
        return switch (failure) {
            case UNKNOWN_TOOL -> UNKNOWN_TOOL;
            case TOOL_NOT_ALLOWED -> TOOL_NOT_ALLOWED;
            case INVALID_TOOL_ARGUMENTS -> INVALID_TOOL_ARGUMENTS;
            case RESOURCE_NOT_AUTHORIZED -> RESOURCE_NOT_AUTHORIZED;
            case TOOL_GATE_REQUIRED -> TOOL_GATE_REQUIRED;
            case TOOL_CONFIRMATION_REQUIRED -> TOOL_CONFIRMATION_REQUIRED;
            case TOOL_APPROVAL_REQUIRED -> TOOL_APPROVAL_REQUIRED;
            case TOOL_EXECUTION_FAILED -> TOOL_EXECUTION_FAILED;
        };
    }
}
