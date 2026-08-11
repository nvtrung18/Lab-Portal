package com.web.labportalbackend.ai.service.impl;

import com.web.labportalbackend.ai.enums.AiAssistantDomain;
import com.web.labportalbackend.ai.enums.AiCapabilityDecisionReason;
import com.web.labportalbackend.ai.service.AiAssistantProfile;
import com.web.labportalbackend.ai.service.AiAssistantRegistry;
import com.web.labportalbackend.ai.service.AiAuthorizedToolPolicy;
import com.web.labportalbackend.ai.service.AiCapabilityDecision;
import com.web.labportalbackend.ai.service.AiToolDefinition;
import com.web.labportalbackend.ai.service.AiToolPolicyDeniedException;
import com.web.labportalbackend.ai.service.AiToolPolicyDenialReason;
import com.web.labportalbackend.ai.service.AiToolPolicyResolver;
import com.web.labportalbackend.ai.service.AiToolRegistry;
import com.web.labportalbackend.ai.service.AiToolRequest;
import org.springframework.stereotype.Service;

/** Fail-closed metadata projection. This class has no collaborator capable of executing a tool. */
@Service
public class AiToolPolicyResolverImpl implements AiToolPolicyResolver {

    private final AiToolRegistry toolRegistry;
    private final AiAssistantRegistry assistantRegistry;

    public AiToolPolicyResolverImpl(AiToolRegistry toolRegistry, AiAssistantRegistry assistantRegistry) {
        if (toolRegistry == null || assistantRegistry == null) {
            throw new IllegalArgumentException("tool and assistant registries are required");
        }
        this.toolRegistry = toolRegistry;
        this.assistantRegistry = assistantRegistry;
    }

    @Override
    public AiAuthorizedToolPolicy resolve(AiCapabilityDecision decision) {
        if (!validDecisionIdentity(decision)) {
            throw denied(AiToolPolicyDenialReason.MALFORMED_DECISION);
        }
        AiToolDefinition definition = toolRegistry.get(decision.capability());
        if (definition == null) {
            throw denied(AiToolPolicyDenialReason.UNKNOWN_TOOL);
        }
        return resolve(decision, new AiToolRequest(definition.id(), definition.schemaVersion(), definition.arguments()));
    }

    @Override
    public AiAuthorizedToolPolicy resolve(AiCapabilityDecision decision, AiToolRequest request) {
        if (!validDecisionIdentity(decision)) {
            throw denied(AiToolPolicyDenialReason.MALFORMED_DECISION);
        }
        if (request == null || request.toolId() == null) {
            throw denied(AiToolPolicyDenialReason.UNKNOWN_TOOL);
        }
        AiToolDefinition definition = toolRegistry.get(request.toolId());
        if (definition == null) {
            throw denied(AiToolPolicyDenialReason.UNKNOWN_TOOL);
        }
        if (!definition.schemaVersion().equals(request.schemaVersion())) {
            throw denied(AiToolPolicyDenialReason.SCHEMA_MISMATCH);
        }
        if (!definition.arguments().equals(request.argumentNames())) {
            throw denied(AiToolPolicyDenialReason.ARGUMENT_MISMATCH);
        }
        if (definition.capability() != decision.capability() || definition.domain() != decision.domain()
                || definition.action() != decision.capability().action()
                || definition.riskBoundary() != decision.riskBoundary()) {
            throw denied(AiToolPolicyDenialReason.TOOL_CAPABILITY_MISMATCH);
        }
        if (!definition.matchesResolvedResource(decision.resolvedResource())) {
            throw denied(AiToolPolicyDenialReason.RESOURCE_MISMATCH);
        }
        validateAuthority(decision, definition);
        return new AiAuthorizedToolPolicy(definition);
    }

    private void validateAuthority(AiCapabilityDecision decision, AiToolDefinition definition) {
        AiAssistantProfile profile;
        try {
            profile = assistantRegistry.getProfile(decision.assistantKey());
        } catch (RuntimeException exception) {
            throw denied(AiToolPolicyDenialReason.PROFILE_MISCONFIGURED);
        }
        if (profile == null || profile.key() != decision.assistantKey() || profile.domain() != decision.domain()
                || !profile.key().matchesDomain(profile.domain()) || profile.toolGroups() == null
                || profile.toolGroups().stream().anyMatch(group -> group == null || !group.belongsTo(profile.domain()))) {
            throw denied(AiToolPolicyDenialReason.PROFILE_MISCONFIGURED);
        }
        if (!profile.catalogEnabled()) {
            throw denied(AiToolPolicyDenialReason.PROFILE_DISABLED);
        }
        if (!profile.toolGroups().contains(definition.group())) {
            throw denied(AiToolPolicyDenialReason.TOOL_GROUP_NOT_ALLOWED);
        }
    }

    private static boolean validDecisionIdentity(AiCapabilityDecision decision) {
        return decision != null && decision.allowed()
                && decision.decisionReason() == AiCapabilityDecisionReason.ALLOWED_BY_EFFECTIVE_PERMISSION
                && positive(decision.acceptedActorId())
                && decision.selectedSystemRole() != null && decision.assistantKey() != null && decision.domain() != null
                && decision.assistantKey().matchesDomain(decision.domain()) && decision.capability() != null
                && decision.capability().domain() == decision.domain() && decision.riskBoundary() != null
                && decision.riskBoundary() == decision.capability().riskBoundary() && decision.resolvedResource() != null;
    }

    private static boolean positive(Long value) {
        return value != null && value > 0;
    }

    private static AiToolPolicyDeniedException denied(AiToolPolicyDenialReason reason) {
        return new AiToolPolicyDeniedException(reason);
    }
}
