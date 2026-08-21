package com.web.labportalbackend.ai.service;

public enum AiToolExecutionFailure {
    UNKNOWN_TOOL,
    TOOL_NOT_ALLOWED,
    INVALID_TOOL_ARGUMENTS,
    RESOURCE_NOT_AUTHORIZED,
    TOOL_GATE_REQUIRED,
    TOOL_EXECUTION_FAILED
}
