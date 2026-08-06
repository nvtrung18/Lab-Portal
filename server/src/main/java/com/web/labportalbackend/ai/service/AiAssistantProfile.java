package com.web.labportalbackend.ai.service;

import com.web.labportalbackend.ai.enums.AiAssistantDomain;
import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.ai.enums.AiAssistantSystemRole;
import com.web.labportalbackend.ai.enums.AiAssistantToolGroup;
import com.web.labportalbackend.ai.enums.AiQuotaPolicyReference;
import java.util.Set;

public record AiAssistantProfile(
        AiAssistantKey key,
        AiAssistantDomain domain,
        boolean catalogEnabled,
        Set<AiAssistantSystemRole> allowedSystemRoles,
        String modelProfile,
        String promptVersion,
        String adapterReference,
        String retrievalNamespace,
        AiQuotaPolicyReference quotaPolicyReference,
        Set<AiAssistantToolGroup> toolGroups,
        String evaluationSuiteVersion) {

    public AiAssistantProfile {
        if (key == null || domain == null || quotaPolicyReference == null) {
            throw new IllegalArgumentException("assistant profile identifiers are required");
        }
        allowedSystemRoles = immutableNonEmptySet(allowedSystemRoles, "allowedSystemRoles");
        toolGroups = immutableNonEmptySet(toolGroups, "toolGroups");
        requireNonBlank(modelProfile, "modelProfile");
        requireNonBlank(promptVersion, "promptVersion");
        if (adapterReference != null) {
            requireNonBlank(adapterReference, "adapterReference");
        }
        requireNonBlank(retrievalNamespace, "retrievalNamespace");
        requireNonBlank(evaluationSuiteVersion, "evaluationSuiteVersion");
    }

    private static <T> Set<T> immutableNonEmptySet(Set<T> values, String name) {
        if (values == null || values.isEmpty() || values.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException(name + " must contain values");
        }
        return Set.copyOf(values);
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
