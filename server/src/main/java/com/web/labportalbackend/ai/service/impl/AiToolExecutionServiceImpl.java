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
import com.web.labportalbackend.ai.service.AiAssistantAvailabilityException;
import com.web.labportalbackend.ai.service.AiAssistantAvailabilityService;
import com.web.labportalbackend.ai.service.AiAssistantProfile;
import com.web.labportalbackend.ai.service.AiAuditExecutionResult;
import com.web.labportalbackend.ai.service.AiAuditFailureCode;
import com.web.labportalbackend.ai.service.AiAuditGateStatus;
import com.web.labportalbackend.ai.service.AiAuditUsageService;
import com.web.labportalbackend.ai.service.AiCapabilityRequest;
import com.web.labportalbackend.ai.service.AiToolActionGateDecision;
import com.web.labportalbackend.ai.service.AiToolActionGateService;
import com.web.labportalbackend.ai.service.AiToolAuditEvent;
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
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public final class AiToolExecutionServiceImpl implements AiToolExecutionService {

    private static final Set<String> REQUEST_FIELDS =
            Set.of("assistantKey", "schemaVersion", "toolId", "arguments");
    private static final Set<String> RESOURCE_FIELDS = Set.of("resourceType", "resourceId");
    private static final int MAX_TOOL_ID_LENGTH = 128;
    private static final Pattern SAFE_AUDIT_TOOL_ID =
            Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$");

    private final AiToolRegistry toolRegistry;
    private final AiAssistantAvailabilityService availabilityService;
    private final AiContextFacade contextFacade;
    private final AiToolActionGateService actionGateService;
    private final AiAuditUsageService auditUsageService;
    private final Map<AiToolId, AiToolHandler> handlers;

    public AiToolExecutionServiceImpl(AiToolRegistry toolRegistry,
                                      AiAssistantAvailabilityService availabilityService,
                                      AiContextFacade contextFacade,
                                      AiToolActionGateService actionGateService,
                                      AiAuditUsageService auditUsageService,
                                      List<AiToolHandler> handlers) {
        if (toolRegistry == null || availabilityService == null || contextFacade == null
                || actionGateService == null || auditUsageService == null || handlers == null) {
            throw new IllegalArgumentException("tool execution dependencies are required");
        }
        this.toolRegistry = toolRegistry;
        this.availabilityService = availabilityService;
        this.contextFacade = contextFacade;
        this.actionGateService = actionGateService;
        this.auditUsageService = auditUsageService;
        this.handlers = validatedHandlers(toolRegistry, handlers);
    }

    @Override
    public AiToolExecutionResult execute(JsonNode untrustedRequest, String requestId) {
        String normalizedRequestId = AiGatewayRequest.normalizeRequestId(requestId);
        ToolAuditState audit = new ToolAuditState(normalizedRequestId, safeRequestedToolId(untrustedRequest));
        AiToolExecutionResult result;
        try {
            result = executeAuthorized(untrustedRequest, normalizedRequestId, audit);
        } catch (RuntimeException exception) {
            auditUsageService.recordToolOutcome(audit.failed(exception));
            throw exception;
        }
        auditUsageService.recordToolOutcome(audit.succeeded());
        return result;
    }

    private AiToolExecutionResult executeAuthorized(JsonNode untrustedRequest,
                                                    String normalizedRequestId,
                                                    ToolAuditState audit) {
        ParsedToolRequest parsed = parse(untrustedRequest, normalizedRequestId, audit);

        AiAssistantAvailability availability = availabilityService.requireAvailableForActor(parsed.assistantKey());
        validateAssistant(parsed.assistantKey(), parsed.definition(), availability, normalizedRequestId);
        audit.authorized(availability);

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
        enforceActionGate(parsed.definition(), normalizedRequestId, audit);

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

    private void enforceActionGate(AiToolDefinition definition, String requestId, ToolAuditState audit) {
        AiToolActionGateDecision decision;
        try {
            decision = actionGateService.classify(definition);
        } catch (RuntimeException exception) {
            throw denied(AiToolExecutionFailure.TOOL_NOT_ALLOWED, requestId);
        }
        if (decision == null) {
            throw denied(AiToolExecutionFailure.TOOL_NOT_ALLOWED, requestId);
        }
        switch (decision) {
            case ALLOW_READ_ONLY -> {
                audit.gate(AiAuditGateStatus.NOT_REQUIRED);
                return;
            }
            case RETURN_DRAFT_ONLY -> {
                audit.gate(AiAuditGateStatus.DRAFT_ONLY_WRITE_BLOCKED);
                throw denied(AiToolExecutionFailure.TOOL_GATE_REQUIRED, requestId);
            }
            case REQUIRE_CONFIRMATION -> {
                audit.gate(AiAuditGateStatus.CONFIRMATION_REQUIRED);
                throw denied(AiToolExecutionFailure.TOOL_CONFIRMATION_REQUIRED, requestId);
            }
            case REQUIRE_APPROVAL -> {
                audit.gate(AiAuditGateStatus.APPROVAL_REQUIRED);
                throw denied(AiToolExecutionFailure.TOOL_APPROVAL_REQUIRED, requestId);
            }
            case DENY -> {
                audit.gate(AiAuditGateStatus.PROHIBITED);
                throw denied(AiToolExecutionFailure.TOOL_NOT_ALLOWED, requestId);
            }
        }
    }

    private ParsedToolRequest parse(JsonNode root, String requestId, ToolAuditState audit) {
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
        audit.definition(definition);
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
        audit.resource(resource);
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

    private static String safeRequestedToolId(JsonNode root) {
        String requested = root != null && root.isObject() ? exactText(root.get("toolId")) : null;
        return requested != null && SAFE_AUDIT_TOOL_ID.matcher(requested).matches() ? requested : null;
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

    private static AiAuditFailureCode auditFailure(RuntimeException exception) {
        if (exception instanceof AiToolExecutionException toolException) {
            return AiAuditFailureCode.from(toolException.failure());
        }
        if (exception instanceof AiAssistantAvailabilityException availabilityException) {
            return AiAuditFailureCode.from(availabilityException.failure());
        }
        return AiAuditFailureCode.INTERNAL_FAILURE;
    }

    private static AiAuditExecutionResult auditResult(RuntimeException exception) {
        if (exception instanceof AiToolExecutionException toolException) {
            return switch (toolException.failure()) {
                case TOOL_GATE_REQUIRED, TOOL_CONFIRMATION_REQUIRED, TOOL_APPROVAL_REQUIRED ->
                        AiAuditExecutionResult.GATE_REQUIRED;
                case TOOL_EXECUTION_FAILED -> AiAuditExecutionResult.FAILED;
                default -> AiAuditExecutionResult.DENIED;
            };
        }
        return exception instanceof AiAssistantAvailabilityException
                ? AiAuditExecutionResult.DENIED : AiAuditExecutionResult.FAILED;
    }

    private static final class ToolAuditState {
        private final String requestId;
        private String requestedToolId;
        private Long actorId;
        private AiAssistantKey assistant;
        private AiToolDefinition definition;
        private ParsedResource resource;
        private String modelVersion;
        private String adapterVersion;
        private String promptVersion;
        private AiAuditGateStatus gateStatus = AiAuditGateStatus.NOT_CLASSIFIED;

        private ToolAuditState(String requestId, String requestedToolId) {
            this.requestId = requestId;
            this.requestedToolId = requestedToolId;
        }

        private void definition(AiToolDefinition definition) {
            this.definition = definition;
            this.requestedToolId = null;
        }

        private void resource(ParsedResource resource) {
            this.resource = resource;
        }

        private void authorized(AiAssistantAvailability availability) {
            AiAssistantProfile profile = availability.profile();
            this.actorId = availability.actorId();
            this.assistant = profile.key();
            this.modelVersion = profile.modelProfile();
            this.adapterVersion = profile.adapterReference();
            this.promptVersion = profile.promptVersion();
        }

        private void gate(AiAuditGateStatus gateStatus) {
            this.gateStatus = gateStatus;
        }

        private AiToolAuditEvent succeeded() {
            return event(AiAuditExecutionResult.SUCCEEDED, null);
        }

        private AiToolAuditEvent failed(RuntimeException exception) {
            return event(auditResult(exception), auditFailure(exception));
        }

        private AiToolAuditEvent event(AiAuditExecutionResult result, AiAuditFailureCode failure) {
            return new AiToolAuditEvent(
                    actorId, assistant, definition == null ? null : definition.id(), requestedToolId,
                    definition == null ? null : definition.capability(),
                    resource == null ? null : resource.type(), resource == null ? null : resource.id(),
                    modelVersion, adapterVersion, promptVersion, requestId, gateStatus, result, failure);
        }
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
