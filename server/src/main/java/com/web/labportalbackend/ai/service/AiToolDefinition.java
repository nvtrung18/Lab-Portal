package com.web.labportalbackend.ai.service;

import com.web.labportalbackend.ai.enums.AiActionRiskBoundary;
import com.web.labportalbackend.ai.enums.AiAssistantDomain;
import com.web.labportalbackend.ai.enums.AiAssistantToolGroup;
import com.web.labportalbackend.ai.enums.AiCapability;
import com.web.labportalbackend.ai.enums.AiRequestedAction;
import com.web.labportalbackend.ai.enums.AiResourceType;
import com.web.labportalbackend.ai.enums.AiToolArgument;
import com.web.labportalbackend.ai.enums.AiToolId;
import java.util.Set;

/** Immutable static metadata. It deliberately has no handler, target, or executor reference. */
public record AiToolDefinition(
        AiToolId id,
        AiAssistantDomain domain,
        AiAssistantToolGroup group,
        AiCapability capability,
        AiResourceType resourceType,
        AiResourceType parentResourceType,
        AiRequestedAction action,
        AiActionRiskBoundary riskBoundary,
        String schemaVersion,
        Set<AiToolArgument> arguments) {

    public AiToolDefinition {
        if (id == null || domain == null || group == null || capability == null || resourceType == null
                || action == null || riskBoundary == null || schemaVersion == null || schemaVersion.isBlank()) {
            throw new IllegalArgumentException("tool definition is incomplete");
        }
        if (!group.belongsTo(domain) || capability.domain() != domain || capability.resourceType() != resourceType
                || capability.parentResourceType() != parentResourceType || capability.action() != action
                || capability.riskBoundary() != riskBoundary) {
            throw new IllegalArgumentException("tool definition authority does not match its capability");
        }
        arguments = arguments == null ? Set.of() : Set.copyOf(arguments);
        Set<AiToolArgument> expected = parentResourceType == null
                ? Set.of(AiToolArgument.RESOURCE)
                : Set.of(AiToolArgument.RESOURCE, AiToolArgument.PARENT_RESOURCE);
        if (!arguments.equals(expected)) {
            throw new IllegalArgumentException("tool definition arguments do not match resource shape");
        }
    }

    public boolean matchesResolvedResource(AiCapabilityDecision.ResolvedResource resource) {
        if (resource == null || resource.type() != resourceType) {
            return false;
        }
        if (!resource.hasValidIdentityShape()) {
            return false;
        }
        return parentResourceType != AiResourceType.PROJECT
                || (positive(resource.projectId()) && resource.groupId() != null && resource.groupId().equals(resource.id()));
    }

    private static boolean positive(Long value) {
        return value != null && value > 0;
    }
}
