package com.web.labportalbackend.ai.service;

import com.web.labportalbackend.ai.client.AiGatewayRequest;
import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.ai.enums.AiCapability;
import com.web.labportalbackend.ai.enums.AiResourceType;
import com.web.labportalbackend.ai.enums.AiToolId;
import java.util.Objects;
import java.util.regex.Pattern;

public record AiToolAuditEvent(
        Long actorId,
        AiAssistantKey assistant,
        AiToolId toolId,
        String requestedToolId,
        AiCapability action,
        AiResourceType resourceType,
        Long resourceId,
        String modelVersion,
        String adapterVersion,
        String promptVersion,
        String requestId,
        AiAuditGateStatus gateStatus,
        AiAuditExecutionResult executionResult,
        AiAuditFailureCode failureCode) {

    private static final Pattern SAFE_REQUESTED_TOOL_ID =
            Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$");

    public AiToolAuditEvent {
        validateOptionalId(actorId, "actorId");
        validateOptionalId(resourceId, "resourceId");
        validateOptionalText(modelVersion, "modelVersion");
        validateOptionalText(adapterVersion, "adapterVersion");
        validateOptionalText(promptVersion, "promptVersion");
        if (requestedToolId != null && !SAFE_REQUESTED_TOOL_ID.matcher(requestedToolId).matches()) {
            throw new IllegalArgumentException("requestedToolId is not audit safe");
        }
        if (!Objects.equals(requestId, AiGatewayRequest.normalizeRequestId(requestId))) {
            throw new IllegalArgumentException("requestId must already be normalized");
        }
        Objects.requireNonNull(gateStatus, "gateStatus is required");
        Objects.requireNonNull(executionResult, "executionResult is required");
        if (resourceType == null && resourceId != null) {
            throw new IllegalArgumentException("resourceType is required when resourceId is present");
        }
        if ((failureCode == null) != (executionResult == AiAuditExecutionResult.SUCCEEDED)) {
            throw new IllegalArgumentException("failureCode must match the execution result");
        }
        if (executionResult == AiAuditExecutionResult.SUCCEEDED
                && (actorId == null || assistant == null || toolId == null || action == null
                || resourceType == null)) {
            throw new IllegalArgumentException("successful tool audit attribution is incomplete");
        }
    }

    private static void validateOptionalId(Long value, String name) {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void validateOptionalText(String value, String name) {
        if (value != null && value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
