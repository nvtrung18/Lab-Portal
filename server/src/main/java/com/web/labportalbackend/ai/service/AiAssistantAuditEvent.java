package com.web.labportalbackend.ai.service;

import com.web.labportalbackend.ai.client.AiGatewayRequest;
import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.ai.enums.AiAssistantSystemRole;
import com.web.labportalbackend.ai.enums.AiCapability;
import com.web.labportalbackend.ai.enums.AiResourceType;
import java.util.Objects;

public record AiAssistantAuditEvent(
        Long actorId,
        AiAssistantKey assistant,
        AiCapability action,
        AiResourceType resourceType,
        Long resourceId,
        String modelVersion,
        String adapterVersion,
        String promptVersion,
        String requestId,
        AiAuditGateStatus gateStatus,
        AiAuditExecutionResult executionResult,
        AiAuditFailureCode failureCode,
        AiAssistantSystemRole role,
        Long labId,
        Long projectId,
        Long groupId,
        Integer promptTokens,
        Integer completionTokens,
        boolean consumesUsage) {

    public AiAssistantAuditEvent {
        validateOptionalId(actorId, "actorId");
        validateOptionalId(resourceId, "resourceId");
        validateOptionalId(labId, "labId");
        validateOptionalId(projectId, "projectId");
        validateOptionalId(groupId, "groupId");
        validateOptionalText(modelVersion, "modelVersion");
        validateOptionalText(adapterVersion, "adapterVersion");
        validateOptionalText(promptVersion, "promptVersion");
        requireNormalizedRequestId(requestId);
        Objects.requireNonNull(gateStatus, "gateStatus is required");
        Objects.requireNonNull(executionResult, "executionResult is required");
        if (resourceType == null && resourceId != null) {
            throw new IllegalArgumentException("resourceType is required when resourceId is present");
        }
        if ((executionResult == AiAuditExecutionResult.SUCCEEDED) != consumesUsage) {
            throw new IllegalArgumentException("only completed assistant operations consume usage");
        }
        if ((failureCode == null) != (executionResult == AiAuditExecutionResult.SUCCEEDED)) {
            throw new IllegalArgumentException("failureCode must match the execution result");
        }
        if (consumesUsage) {
            if (actorId == null || assistant == null || action == null || resourceType == null || role == null
                    || modelVersion == null || promptVersion == null || promptTokens == null
                    || completionTokens == null || promptTokens < 0 || completionTokens < 0) {
                throw new IllegalArgumentException("completed assistant usage attribution is incomplete");
            }
        } else if (promptTokens != null || completionTokens != null) {
            throw new IllegalArgumentException("non-counted assistant outcomes cannot claim token usage");
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

    private static void requireNormalizedRequestId(String requestId) {
        if (!Objects.equals(requestId, AiGatewayRequest.normalizeRequestId(requestId))) {
            throw new IllegalArgumentException("requestId must already be normalized");
        }
    }
}
