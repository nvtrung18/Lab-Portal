package com.web.labportalbackend.ai.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.web.labportalbackend.ai.context.AiAuthorizedContext;
import com.web.labportalbackend.ai.context.AiContextBuildRequest;
import com.web.labportalbackend.ai.context.AiContextFacade;
import com.web.labportalbackend.ai.context.AiContextReadDeniedException;
import com.web.labportalbackend.ai.context.AiDomainContext;
import com.web.labportalbackend.ai.context.AiLabContext;
import com.web.labportalbackend.ai.enums.AiActionRiskBoundary;
import com.web.labportalbackend.ai.enums.AiAssistantDomain;
import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.ai.enums.AiAssistantSystemRole;
import com.web.labportalbackend.ai.enums.AiAssistantToolGroup;
import com.web.labportalbackend.ai.enums.AiCapability;
import com.web.labportalbackend.ai.enums.AiQuotaPolicyReference;
import com.web.labportalbackend.ai.enums.AiResourceScope;
import com.web.labportalbackend.ai.enums.AiResourceType;
import com.web.labportalbackend.ai.enums.AiToolId;
import com.web.labportalbackend.ai.service.AiAssistantAvailability;
import com.web.labportalbackend.ai.service.AiAssistantAvailabilityService;
import com.web.labportalbackend.ai.service.AiAssistantProfile;
import com.web.labportalbackend.ai.service.AiAuthorizedToolPolicy;
import com.web.labportalbackend.ai.service.AiCapabilityDecision;
import com.web.labportalbackend.ai.service.AiCapabilityRequest;
import com.web.labportalbackend.ai.service.AiToolExecutionException;
import com.web.labportalbackend.ai.service.AiToolExecutionFailure;
import com.web.labportalbackend.ai.service.AiToolExecutionResult;
import com.web.labportalbackend.ai.service.AiToolHandler;
import com.web.labportalbackend.ai.service.AiToolDefinition;
import com.web.labportalbackend.ai.service.AiToolRegistry;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;

class AiToolExecutionServiceImplTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private final AiToolRegistry registry = new AiToolRegistryServiceImpl();
    private final AiAssistantAvailabilityService availabilityService = mock(AiAssistantAvailabilityService.class);
    private final AiContextFacade contextFacade = mock(AiContextFacade.class);

    @Test
    void executesOnlyTheExactRegisteredReadHandlerAfterFreshAuthorization() {
        AiToolHandler slotHandler = handler(AiToolId.LAB_SLOT_READ);
        AiToolHandler policyHandler = handler(AiToolId.LAB_POLICY_READ);
        AiToolExecutionServiceImpl service = service(slotHandler, policyHandler);
        AiAuthorizedContext authorized = context(AiCapability.LAB_SLOT_READ, 17L, null, "request-123");
        when(availabilityService.requireAvailableForActor(AiAssistantKey.LAB_ASSISTANT))
                .thenReturn(availability(profile(AiAssistantKey.LAB_ASSISTANT, AiAssistantToolGroup.LAB_READ)));
        when(contextFacade.build(any())).thenReturn(authorized);
        when(slotHandler.execute(AiToolId.LAB_SLOT_READ, authorized)).thenReturn(authorized.context());

        AiToolExecutionResult result = service.execute(
                request(AiAssistantKey.LAB_ASSISTANT, AiToolId.LAB_SLOT_READ, 17L, null), " request-123 ");

        AiCapabilityRequest expectedCapabilityRequest = capabilityRequest(
                AiAssistantKey.LAB_ASSISTANT, AiCapability.LAB_SLOT_READ, 17L, null);
        InOrder order = inOrder(availabilityService, contextFacade, slotHandler);
        order.verify(availabilityService).requireAvailableForActor(AiAssistantKey.LAB_ASSISTANT);
        order.verify(contextFacade).build(new AiContextBuildRequest(expectedCapabilityRequest, "request-123"));
        order.verify(slotHandler).execute(AiToolId.LAB_SLOT_READ, authorized);
        verify(policyHandler, never()).execute(any(), any());
        assertEquals("request-123", result.requestId());
        assertEquals("lab.slot.read", result.toolId());
        assertSame(authorized.context(), result.data());

        JsonNode serialized = OBJECT_MAPPER.valueToTree(result);
        assertEquals(Set.of("requestId", "toolId", "data"), fieldSet(serialized));
        assertFalse(serialized.toString().contains("jwt"));
        assertFalse(serialized.toString().contains("token"));
        assertFalse(serialized.toString().contains("stackTrace"));
    }

    @Test
    void rejectsUnknownRandomAndCaseVariantToolsWithoutAnyFallback() {
        AiToolHandler handler = handler(AiToolId.LAB_SLOT_READ);
        AiToolExecutionServiceImpl service = service(handler);

        for (String toolId : List.of("model.random.tool", "LAB.SLOT.READ", "lab.slot.read ")) {
            ObjectNode request = request(AiAssistantKey.LAB_ASSISTANT, AiToolId.LAB_SLOT_READ, 17L, null);
            request.put("toolId", toolId);

            assertFailure(AiToolExecutionFailure.UNKNOWN_TOOL,
                    () -> service.execute(request, "request-unknown"));
        }

        verify(availabilityService, never()).requireAvailableForActor(any());
        verify(contextFacade, never()).build(any());
        verify(handler, never()).execute(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"approved", "authorized", "executeNow", "bypassConfirmation", "adminOverride",
            "confirmed", "springBean", "javaClass", "methodName", "endpoint", "url", "sql"})
    void rejectsAuthorityAndDynamicDispatchFields(String untrustedField) {
        AiToolHandler handler = handler(AiToolId.LAB_SLOT_READ);
        AiToolExecutionServiceImpl service = service(handler);
        ObjectNode request = request(AiAssistantKey.LAB_ASSISTANT, AiToolId.LAB_SLOT_READ, 17L, null);
        request.put(untrustedField, "attacker-controlled");

        assertFailure(AiToolExecutionFailure.INVALID_TOOL_ARGUMENTS,
                () -> service.execute(request, "request-injected"));

        verify(availabilityService, never()).requireAvailableForActor(any());
        verify(contextFacade, never()).build(any());
        verify(handler, never()).execute(any(), any());
    }

    @Test
    void rejectsMalformedClosedArgumentsBeforeAuthorizationOrExecution() {
        AiToolHandler handler = handler(AiToolId.LAB_SLOT_READ);
        AiToolExecutionServiceImpl service = service(handler);

        ObjectNode missingResource = request(AiAssistantKey.LAB_ASSISTANT, AiToolId.LAB_SLOT_READ, 17L, null);
        ((ObjectNode) missingResource.path("arguments")).remove("resource");
        ObjectNode unknownArgument = request(AiAssistantKey.LAB_ASSISTANT, AiToolId.LAB_SLOT_READ, 17L, null);
        ((ObjectNode) unknownArgument.path("arguments")).put("sql", "select * from users");
        ObjectNode wrongArgumentType = request(AiAssistantKey.LAB_ASSISTANT, AiToolId.LAB_SLOT_READ, 17L, null);
        ((ObjectNode) wrongArgumentType.path("arguments")).put("resource", "slot-17");
        ObjectNode stringIdentifier = request(AiAssistantKey.LAB_ASSISTANT, AiToolId.LAB_SLOT_READ, 17L, null);
        ((ObjectNode) stringIdentifier.path("arguments").path("resource")).put("resourceId", "17");
        ObjectNode nonPositiveIdentifier = request(AiAssistantKey.LAB_ASSISTANT, AiToolId.LAB_SLOT_READ, 17L, null);
        ((ObjectNode) nonPositiveIdentifier.path("arguments").path("resource")).put("resourceId", 0);
        ObjectNode wrongResourceType = request(AiAssistantKey.LAB_ASSISTANT, AiToolId.LAB_SLOT_READ, 17L, null);
        ((ObjectNode) wrongResourceType.path("arguments").path("resource")).put("resourceType", "BOOKING");
        ObjectNode unknownResourceField = request(AiAssistantKey.LAB_ASSISTANT, AiToolId.LAB_SLOT_READ, 17L, null);
        ((ObjectNode) unknownResourceField.path("arguments").path("resource")).put("url", "https://internal");
        ObjectNode unexpectedParent = request(AiAssistantKey.LAB_ASSISTANT, AiToolId.LAB_SLOT_READ, 17L, null);
        ((ObjectNode) unexpectedParent.path("arguments")).set("parentResource", reference("LABORATORY", 10L));
        ObjectNode wrongSchema = request(AiAssistantKey.LAB_ASSISTANT, AiToolId.LAB_SLOT_READ, 17L, null);
        wrongSchema.put("schemaVersion", "v2");

        for (JsonNode malformed : List.of(missingResource, unknownArgument, wrongArgumentType, stringIdentifier,
                nonPositiveIdentifier, wrongResourceType, unknownResourceField, unexpectedParent, wrongSchema)) {
            assertFailure(AiToolExecutionFailure.INVALID_TOOL_ARGUMENTS,
                    () -> service.execute(malformed, "request-invalid"));
        }

        verify(availabilityService, never()).requireAvailableForActor(any());
        verify(contextFacade, never()).build(any());
        verify(handler, never()).execute(any(), any());
    }

    @Test
    void assistantKeyTamperingAndForeignToolGroupsCannotBroadenExecution() {
        AiToolHandler handler = handler(AiToolId.RESEARCH_ASSIGNED_TASK_READ);
        AiToolExecutionServiceImpl service = service(handler);
        when(availabilityService.requireAvailableForActor(AiAssistantKey.LAB_ASSISTANT))
                .thenReturn(availability(profile(AiAssistantKey.LAB_ASSISTANT, AiAssistantToolGroup.LAB_READ)));

        assertFailure(AiToolExecutionFailure.TOOL_NOT_ALLOWED,
                () -> service.execute(request(AiAssistantKey.LAB_ASSISTANT,
                        AiToolId.RESEARCH_ASSIGNED_TASK_READ, 40L, null), "request-foreign"));

        verify(contextFacade, never()).build(any());
        verify(handler, never()).execute(any(), any());
    }

    @Test
    void actorToolGroupDenialPreventsFreshContextAndHandlerInvocation() {
        AiToolHandler handler = handler(AiToolId.LAB_SLOT_READ);
        AiToolExecutionServiceImpl service = service(handler);
        when(availabilityService.requireAvailableForActor(AiAssistantKey.LAB_ASSISTANT))
                .thenReturn(availability(profile(AiAssistantKey.LAB_ASSISTANT, AiAssistantToolGroup.LAB_DRAFT)));

        assertFailure(AiToolExecutionFailure.TOOL_NOT_ALLOWED,
                () -> service.execute(request(AiAssistantKey.LAB_ASSISTANT,
                        AiToolId.LAB_SLOT_READ, 17L, null), "request-group-denied"));

        verify(contextFacade, never()).build(any());
        verify(handler, never()).execute(any(), any());
    }

    @Test
    void currentCapabilityOrBusinessPermissionDenialNeverInvokesHandler() {
        AiToolHandler handler = handler(AiToolId.LAB_SLOT_READ);
        AiToolExecutionServiceImpl service = service(handler);
        when(availabilityService.requireAvailableForActor(AiAssistantKey.LAB_ASSISTANT))
                .thenReturn(availability(profile(AiAssistantKey.LAB_ASSISTANT, AiAssistantToolGroup.LAB_READ)));
        when(contextFacade.build(any())).thenThrow(new AiContextReadDeniedException());

        AiToolExecutionException exception = assertFailure(AiToolExecutionFailure.RESOURCE_NOT_AUTHORIZED,
                () -> service.execute(request(AiAssistantKey.LAB_ASSISTANT,
                        AiToolId.LAB_SLOT_READ, 17L, null), "request-revoked"));

        assertEquals("request-revoked", exception.requestId());
        verify(handler, never()).execute(any(), any());
    }

    @Test
    void mismatchedAuthorizedResourceFailsClosedBeforeHandlerInvocation() {
        AiToolHandler handler = handler(AiToolId.LAB_SLOT_READ);
        AiToolExecutionServiceImpl service = service(handler);
        when(availabilityService.requireAvailableForActor(AiAssistantKey.LAB_ASSISTANT))
                .thenReturn(availability(profile(AiAssistantKey.LAB_ASSISTANT, AiAssistantToolGroup.LAB_READ)));
        when(contextFacade.build(any())).thenReturn(context(AiCapability.LAB_SLOT_READ, 99L, null, "request-cross"));

        assertFailure(AiToolExecutionFailure.RESOURCE_NOT_AUTHORIZED,
                () -> service.execute(request(AiAssistantKey.LAB_ASSISTANT,
                        AiToolId.LAB_SLOT_READ, 17L, null), "request-cross"));

        verify(handler, never()).execute(any(), any());
    }

    @Test
    void nonCanonicalRecomputedToolDescriptorFailsClosedBeforeHandlerInvocation() {
        AiToolHandler handler = handler(AiToolId.LAB_SLOT_READ);
        AiToolExecutionServiceImpl service = service(handler);
        AiAuthorizedContext valid = context(AiCapability.LAB_SLOT_READ, 17L, null, "request-descriptor");
        AiToolDefinition canonical = valid.toolPolicy().descriptor();
        AiToolDefinition tampered = new AiToolDefinition(
                canonical.id(), canonical.domain(), canonical.group(), canonical.capability(),
                canonical.resourceType(), canonical.parentResourceType(), canonical.action(),
                canonical.riskBoundary(), "attacker-schema", canonical.arguments());
        AiAuthorizedContext returned = new AiAuthorizedContext(
                valid.requestId(), valid.assistantKey(), valid.domain(), valid.capability(), valid.resource(),
                new AiAuthorizedToolPolicy(tampered), valid.contextVersion(), valid.builtAt(),
                valid.freshness(), valid.context());
        when(availabilityService.requireAvailableForActor(AiAssistantKey.LAB_ASSISTANT))
                .thenReturn(availability(profile(AiAssistantKey.LAB_ASSISTANT, AiAssistantToolGroup.LAB_READ)));
        when(contextFacade.build(any())).thenReturn(returned);

        assertFailure(AiToolExecutionFailure.RESOURCE_NOT_AUTHORIZED,
                () -> service.execute(request(AiAssistantKey.LAB_ASSISTANT,
                        AiToolId.LAB_SLOT_READ, 17L, null), "request-descriptor"));

        verify(handler, never()).execute(any(), any());
    }

    @Test
    void nonReadToolRequiresTheFutureGateAfterFreshAuthorizationAndNeverExecutes() {
        AiToolHandler readHandler = handler(AiToolId.LAB_SLOT_READ);
        AiToolExecutionServiceImpl service = service(readHandler);
        AiAuthorizedContext authorized = context(AiCapability.LAB_BOOKING_DRAFT, 17L, null, "request-gated");
        when(availabilityService.requireAvailableForActor(AiAssistantKey.LAB_ASSISTANT))
                .thenReturn(availability(profile(AiAssistantKey.LAB_ASSISTANT, AiAssistantToolGroup.LAB_DRAFT)));
        when(contextFacade.build(any())).thenReturn(authorized);

        AiToolExecutionException exception = assertFailure(AiToolExecutionFailure.TOOL_GATE_REQUIRED,
                () -> service.execute(request(AiAssistantKey.LAB_ASSISTANT,
                        AiToolId.LAB_BOOKING_DRAFT, 17L, null), "request-gated"));

        assertEquals("request-gated", exception.requestId());
        verify(contextFacade).build(any());
        verify(readHandler, never()).execute(any(), any());
    }

    @Test
    void registeredHandlerFailureIsSanitizedAndInvokedOnlyOnce() {
        AiToolHandler handler = handler(AiToolId.LAB_SLOT_READ);
        AiToolExecutionServiceImpl service = service(handler);
        AiAuthorizedContext authorized = context(AiCapability.LAB_SLOT_READ, 17L, null, "request-failed");
        when(availabilityService.requireAvailableForActor(AiAssistantKey.LAB_ASSISTANT))
                .thenReturn(availability(profile(AiAssistantKey.LAB_ASSISTANT, AiAssistantToolGroup.LAB_READ)));
        when(contextFacade.build(any())).thenReturn(authorized);
        when(handler.execute(AiToolId.LAB_SLOT_READ, authorized))
                .thenThrow(new IllegalStateException("secret-token internal.service.Bean"));

        AiToolExecutionException exception = assertFailure(AiToolExecutionFailure.TOOL_EXECUTION_FAILED,
                () -> service.execute(request(AiAssistantKey.LAB_ASSISTANT,
                        AiToolId.LAB_SLOT_READ, 17L, null), "request-failed"));

        assertEquals("Tool execution failed", exception.getMessage());
        assertEquals("request-failed", exception.requestId());
        assertNull(exception.getCause());
        verify(handler).execute(AiToolId.LAB_SLOT_READ, authorized);
    }

    @Test
    void unregisteredReadToolAndDuplicateHandlerRegistrationFailClosed() {
        AiToolExecutionServiceImpl noHandlers = service();
        when(availabilityService.requireAvailableForActor(AiAssistantKey.LAB_ASSISTANT))
                .thenReturn(availability(profile(AiAssistantKey.LAB_ASSISTANT, AiAssistantToolGroup.LAB_READ)));
        when(contextFacade.build(any())).thenReturn(context(
                AiCapability.LAB_SLOT_READ, 17L, null, "request-unregistered"));

        assertFailure(AiToolExecutionFailure.TOOL_NOT_ALLOWED,
                () -> noHandlers.execute(request(AiAssistantKey.LAB_ASSISTANT,
                        AiToolId.LAB_SLOT_READ, 17L, null), "request-unregistered"));

        AiToolHandler first = handler(AiToolId.LAB_SLOT_READ);
        AiToolHandler duplicate = handler(AiToolId.LAB_SLOT_READ);
        assertThrows(IllegalArgumentException.class, () -> service(first, duplicate));
    }

    private AiToolExecutionServiceImpl service(AiToolHandler... handlers) {
        return new AiToolExecutionServiceImpl(registry, availabilityService, contextFacade, List.of(handlers));
    }

    private static AiToolHandler handler(AiToolId toolId) {
        AiToolHandler handler = mock(AiToolHandler.class);
        when(handler.supportedToolIds()).thenReturn(Set.of(toolId));
        return handler;
    }

    private static ObjectNode request(AiAssistantKey assistantKey, AiToolId toolId,
                                      Long resourceId, Long parentResourceId) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("assistantKey", assistantKey.name());
        root.put("schemaVersion", "v1");
        root.put("toolId", toolId.value());
        ObjectNode arguments = root.putObject("arguments");
        AiCapability capability = new AiToolRegistryServiceImpl().get(toolId).capability();
        arguments.set("resource", reference(capability.resourceType().name(), resourceId));
        if (capability.parentResourceType() != null) {
            arguments.set("parentResource", reference(capability.parentResourceType().name(), parentResourceId));
        }
        return root;
    }

    private static ObjectNode reference(String resourceType, Long resourceId) {
        ObjectNode reference = OBJECT_MAPPER.createObjectNode();
        reference.put("resourceType", resourceType);
        if (resourceId == null) {
            reference.putNull("resourceId");
        } else {
            reference.put("resourceId", resourceId);
        }
        return reference;
    }

    private static AiAssistantAvailability availability(AiAssistantProfile profile) {
        return new AiAssistantAvailability(profile, 7L, AiAssistantSystemRole.STUDENT);
    }

    private static AiAssistantProfile profile(AiAssistantKey key, AiAssistantToolGroup group) {
        return new AiAssistantProfile(key, key.domain(), true,
                Set.of(AiAssistantSystemRole.STUDENT), "model", "prompt-v1", null,
                "namespace", AiQuotaPolicyReference.AI_CONFIG_QUOTA, Set.of(group), "suite-v1");
    }

    private static AiCapabilityRequest capabilityRequest(AiAssistantKey key, AiCapability capability,
                                                          Long resourceId, Long parentResourceId) {
        return new AiCapabilityRequest(key, 7L, capability,
                new AiCapabilityRequest.ResourceReference(capability.resourceType(), resourceId),
                capability.parentResourceType() == null ? null
                        : new AiCapabilityRequest.ResourceReference(
                                capability.parentResourceType(), parentResourceId),
                capability.action());
    }

    private static AiAuthorizedContext context(AiCapability capability, Long resourceId,
                                               Long parentResourceId, String requestId) {
        AiCapabilityDecision.ResolvedResource resource = new AiCapabilityDecision.ResolvedResource(
                capability.resourceType(), resourceId, 10L, null, null, null,
                AiResourceScope.EXISTING_BUSINESS_PERMISSION);
        AiDomainContext domainContext = new AiLabContext(
                new AiLabContext.Laboratory(10L, "Authorized Lab", null),
                new AiLabContext.Slot(resourceId, Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-01-01T01:00:00Z"), null),
                null, null, capability.riskBoundary() == AiActionRiskBoundary.DRAFT_ONLY,
                capability.riskBoundary().name());
        return new AiAuthorizedContext(requestId, AiAssistantKey.LAB_ASSISTANT, AiAssistantDomain.LAB,
                capability, resource,
                new AiAuthorizedToolPolicy(new AiToolRegistryServiceImpl().get(capability)),
                "P5A-T5-v1", Instant.parse("2026-01-01T00:00:00Z"),
                AiAuthorizedContext.Freshness.LIVE_READ_NO_CACHE, domainContext);
    }

    private static Set<String> fieldSet(JsonNode node) {
        Set<String> fields = new HashSet<>();
        node.fieldNames().forEachRemaining(fields::add);
        return fields;
    }

    private static AiToolExecutionException assertFailure(AiToolExecutionFailure expected,
                                                           org.junit.jupiter.api.function.Executable executable) {
        AiToolExecutionException exception = assertThrows(AiToolExecutionException.class, executable);
        assertEquals(expected, exception.failure());
        return exception;
    }
}
