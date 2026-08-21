package com.web.labportalbackend.ai.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.web.labportalbackend.ai.client.AiGatewayRequest;
import com.web.labportalbackend.ai.context.AiAdminContext;
import com.web.labportalbackend.ai.context.AiAuthorizedContext;
import com.web.labportalbackend.ai.context.AiContextBuildRequest;
import com.web.labportalbackend.ai.context.AiContextFacade;
import com.web.labportalbackend.ai.context.AiDomainContext;
import com.web.labportalbackend.ai.context.AiLabContext;
import com.web.labportalbackend.ai.context.AiResearchAssistantContext;
import com.web.labportalbackend.ai.enums.AiActionRiskBoundary;
import com.web.labportalbackend.ai.enums.AiAssistantDomain;
import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.ai.enums.AiResourceType;
import com.web.labportalbackend.ai.enums.AiToolArgument;
import com.web.labportalbackend.ai.enums.AiToolId;
import com.web.labportalbackend.ai.service.AiAssistantAvailability;
import com.web.labportalbackend.ai.service.AiAssistantAvailabilityService;
import com.web.labportalbackend.ai.service.AiAssistantProfile;
import com.web.labportalbackend.ai.service.AiCapabilityRequest;
import com.web.labportalbackend.ai.service.AiToolDefinition;
import com.web.labportalbackend.ai.service.AiToolExecutionException;
import com.web.labportalbackend.ai.service.AiToolExecutionFailure;
import com.web.labportalbackend.ai.service.AiToolExecutionResult;
import com.web.labportalbackend.ai.service.AiToolExecutionService;
import com.web.labportalbackend.ai.service.AiToolHandler;
import com.web.labportalbackend.ai.service.AiToolRegistry;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public final class AiToolExecutionServiceImpl implements AiToolExecutionService {

    private static final Set<String> REQUEST_FIELDS =
            Set.of("assistantKey", "schemaVersion", "toolId", "arguments");
    private static final Set<String> RESOURCE_FIELDS = Set.of("resourceType", "resourceId");
    private static final int MAX_TOOL_ID_LENGTH = 128;

    private final AiToolRegistry toolRegistry;
    private final AiAssistantAvailabilityService availabilityService;
    private final AiContextFacade contextFacade;
    private final Map<AiToolId, AiToolHandler> handlers;

    public AiToolExecutionServiceImpl(AiToolRegistry toolRegistry,
                                      AiAssistantAvailabilityService availabilityService,
                                      AiContextFacade contextFacade,
                                      List<AiToolHandler> handlers) {
        if (toolRegistry == null || availabilityService == null || contextFacade == null || handlers == null) {
            throw new IllegalArgumentException("tool execution dependencies are required");
        }
        this.toolRegistry = toolRegistry;
        this.availabilityService = availabilityService;
        this.contextFacade = contextFacade;
        this.handlers = validatedHandlers(toolRegistry, handlers);
    }

    @Override
    public AiToolExecutionResult execute(JsonNode untrustedRequest, String requestId) {
        String normalizedRequestId = AiGatewayRequest.normalizeRequestId(requestId);
        ParsedToolRequest parsed = parse(untrustedRequest, normalizedRequestId);

        AiAssistantAvailability availability = availabilityService.requireAvailableForActor(parsed.assistantKey());
        validateAssistant(parsed.assistantKey(), parsed.definition(), availability, normalizedRequestId);

        AiCapabilityRequest capabilityRequest = new AiCapabilityRequest(
                parsed.assistantKey(), availability.actorId(), parsed.definition().capability(),
                parsed.resource().toCapabilityReference(),
                parsed.parentResource() == null ? null : parsed.parentResource().toCapabilityReference(),
                parsed.definition().action());
        AiAuthorizedContext authorized;
        try {
            authorized = contextFacade.build(new AiContextBuildRequest(capabilityRequest, normalizedRequestId));
        } catch (RuntimeException exception) {
            throw denied(AiToolExecutionFailure.RESOURCE_NOT_AUTHORIZED, normalizedRequestId);
        }
        if (!matchesCurrentAuthority(parsed, availability, authorized, normalizedRequestId)) {
            throw denied(AiToolExecutionFailure.RESOURCE_NOT_AUTHORIZED, normalizedRequestId);
        }
        if (parsed.definition().riskBoundary() != AiActionRiskBoundary.READ_ONLY) {
            throw denied(AiToolExecutionFailure.TOOL_GATE_REQUIRED, normalizedRequestId);
        }

        AiToolHandler handler = handlers.get(parsed.definition().id());
        if (handler == null) {
            throw denied(AiToolExecutionFailure.TOOL_NOT_ALLOWED, normalizedRequestId);
        }
        AiDomainContext result;
        try {
            result = handler.execute(parsed.definition().id(), authorized);
        } catch (RuntimeException exception) {
            throw denied(AiToolExecutionFailure.TOOL_EXECUTION_FAILED, normalizedRequestId);
        }
        if (!matchesDomain(parsed.definition().domain(), result)) {
            throw denied(AiToolExecutionFailure.TOOL_EXECUTION_FAILED, normalizedRequestId);
        }
        return new AiToolExecutionResult(normalizedRequestId, parsed.definition().id().value(), result);
    }

    private ParsedToolRequest parse(JsonNode root, String requestId) {
        if (!hasExactFields(root, REQUEST_FIELDS)) {
            throw denied(AiToolExecutionFailure.INVALID_TOOL_ARGUMENTS, requestId);
        }
        String externalToolId = exactText(root.get("toolId"));
        if (externalToolId == null || externalToolId.length() > MAX_TOOL_ID_LENGTH) {
            throw denied(AiToolExecutionFailure.UNKNOWN_TOOL, requestId);
        }
        AiToolDefinition definition = toolRegistry.get(externalToolId);
        if (definition == null) {
            throw denied(AiToolExecutionFailure.UNKNOWN_TOOL, requestId);
        }
        AiAssistantKey assistantKey = assistantKey(root.get("assistantKey"), requestId);
        if (definition.domain() != assistantKey.domain()) {
            throw denied(AiToolExecutionFailure.TOOL_NOT_ALLOWED, requestId);
        }
        if (!definition.schemaVersion().equals(exactText(root.get("schemaVersion")))) {
            throw denied(AiToolExecutionFailure.INVALID_TOOL_ARGUMENTS, requestId);
        }

        JsonNode arguments = root.get("arguments");
        Set<String> expectedArguments = definition.arguments().contains(AiToolArgument.PARENT_RESOURCE)
                ? Set.of("resource", "parentResource") : Set.of("resource");
        if (!hasExactFields(arguments, expectedArguments)) {
            throw denied(AiToolExecutionFailure.INVALID_TOOL_ARGUMENTS, requestId);
        }
        ParsedResource resource = resource(arguments.get("resource"), definition.resourceType(), requestId);
        ParsedResource parent = definition.parentResourceType() == null ? null
                : resource(arguments.get("parentResource"), definition.parentResourceType(), requestId);
        return new ParsedToolRequest(assistantKey, definition, resource, parent);
    }

    private static AiAssistantKey assistantKey(JsonNode node, String requestId) {
        String value = exactText(node);
        if (value == null) {
            throw denied(AiToolExecutionFailure.TOOL_NOT_ALLOWED, requestId);
        }
        try {
            return AiAssistantKey.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw denied(AiToolExecutionFailure.TOOL_NOT_ALLOWED, requestId);
        }
    }

    private static ParsedResource resource(JsonNode node, AiResourceType expectedType, String requestId) {
        if (!hasExactFields(node, RESOURCE_FIELDS) || !expectedType.name().equals(exactText(node.get("resourceType")))) {
            throw denied(AiToolExecutionFailure.INVALID_TOOL_ARGUMENTS, requestId);
        }
        JsonNode id = node.get("resourceId");
        if (isGlobal(expectedType)) {
            if (!id.isNull()) {
                throw denied(AiToolExecutionFailure.INVALID_TOOL_ARGUMENTS, requestId);
            }
            return new ParsedResource(expectedType, null);
        }
        if (!id.isIntegralNumber() || !id.canConvertToLong() || id.longValue() <= 0) {
            throw denied(AiToolExecutionFailure.INVALID_TOOL_ARGUMENTS, requestId);
        }
        return new ParsedResource(expectedType, id.longValue());
    }

    private static void validateAssistant(AiAssistantKey requestedAssistant,
                                          AiToolDefinition definition,
                                          AiAssistantAvailability availability,
                                          String requestId) {
        AiAssistantProfile profile = availability == null ? null : availability.profile();
        if (profile == null || availability.actorId() == null || availability.actorId() <= 0
                || availability.selectedSystemRole() == null || profile.key() != requestedAssistant
                || !profile.catalogEnabled() || profile.domain() != definition.domain()
                || profile.key().domain() != definition.domain()
                || profile.allowedSystemRoles() == null
                || !profile.allowedSystemRoles().contains(availability.selectedSystemRole())
                || profile.toolGroups() == null || !profile.toolGroups().contains(definition.group())) {
            throw denied(AiToolExecutionFailure.TOOL_NOT_ALLOWED, requestId);
        }
    }

    private static boolean matchesCurrentAuthority(ParsedToolRequest parsed,
                                                   AiAssistantAvailability availability,
                                                   AiAuthorizedContext authorized,
                                                   String requestId) {
        if (authorized == null || authorized.toolPolicy() == null || authorized.toolPolicy().descriptor() == null
                || authorized.assistantKey() != parsed.assistantKey()
                || authorized.assistantKey() != availability.profile().key()
                || authorized.domain() != parsed.definition().domain()
                || authorized.capability() != parsed.definition().capability()
                || !authorized.toolPolicy().descriptor().equals(parsed.definition())
                || !Objects.equals(authorized.requestId(), requestId)
                || authorized.resource() == null
                || authorized.resource().type() != parsed.resource().type()
                || !Objects.equals(authorized.resource().id(), parsed.resource().id())) {
            return false;
        }
        if (parsed.parentResource() == null) {
            return parsed.definition().parentResourceType() == null;
        }
        return parsed.definition().parentResourceType() == parsed.parentResource().type()
                && Objects.equals(parentId(authorized, parsed.parentResource().type()), parsed.parentResource().id());
    }

    private static Long parentId(AiAuthorizedContext authorized, AiResourceType parentType) {
        return switch (parentType) {
            case LABORATORY -> authorized.resource().labId();
            case PROJECT -> authorized.resource().projectId();
            case GROUP -> authorized.resource().groupId();
            case TASK -> authorized.resource().taskId();
            default -> null;
        };
    }

    private static Map<AiToolId, AiToolHandler> validatedHandlers(AiToolRegistry registry,
                                                                  List<AiToolHandler> supplied) {
        Map<AiToolId, AiToolHandler> result = new EnumMap<>(AiToolId.class);
        for (AiToolHandler handler : supplied) {
            Set<AiToolId> toolIds = handler == null ? null : handler.supportedToolIds();
            if (toolIds == null || toolIds.isEmpty() || toolIds.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("tool handlers must declare exact canonical IDs");
            }
            for (AiToolId toolId : toolIds) {
                AiToolDefinition definition = registry.get(toolId);
                if (definition == null || definition.riskBoundary() != AiActionRiskBoundary.READ_ONLY
                        || result.put(toolId, handler) != null) {
                    throw new IllegalArgumentException("tool handler registration is invalid");
                }
            }
        }
        return Map.copyOf(result);
    }

    private static boolean hasExactFields(JsonNode node, Set<String> expected) {
        if (node == null || !node.isObject() || node.size() != expected.size()) {
            return false;
        }
        Set<String> actual = new HashSet<>();
        node.fieldNames().forEachRemaining(actual::add);
        return actual.equals(expected);
    }

    private static String exactText(JsonNode node) {
        return node != null && node.isTextual() && !node.textValue().isBlank() ? node.textValue() : null;
    }

    private static boolean isGlobal(AiResourceType type) {
        return type == AiResourceType.SYSTEM || type == AiResourceType.AUDIT_LOG
                || type == AiResourceType.SYSTEM_CONFIG;
    }

    private static boolean matchesDomain(AiAssistantDomain domain, AiDomainContext context) {
        return context != null && switch (domain) {
            case ADMIN -> context instanceof AiAdminContext;
            case LAB -> context instanceof AiLabContext;
            case RESEARCH -> context instanceof AiResearchAssistantContext;
        };
    }

    private static AiToolExecutionException denied(AiToolExecutionFailure failure, String requestId) {
        return new AiToolExecutionException(failure, requestId);
    }

    private record ParsedToolRequest(AiAssistantKey assistantKey, AiToolDefinition definition,
                                     ParsedResource resource, ParsedResource parentResource) {
    }

    private record ParsedResource(AiResourceType type, Long id) {
        AiCapabilityRequest.ResourceReference toCapabilityReference() {
            return new AiCapabilityRequest.ResourceReference(type, id);
        }
    }
}
