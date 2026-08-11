package com.web.labportalbackend.ai.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.web.labportalbackend.ai.enums.AiActionRiskBoundary;
import com.web.labportalbackend.ai.enums.AiAssistantDomain;
import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.ai.enums.AiAssistantSystemRole;
import com.web.labportalbackend.ai.enums.AiAssistantToolGroup;
import com.web.labportalbackend.ai.enums.AiCapability;
import com.web.labportalbackend.ai.enums.AiCapabilityDecisionReason;
import com.web.labportalbackend.ai.enums.AiQuotaPolicyReference;
import com.web.labportalbackend.ai.enums.AiRequestedAction;
import com.web.labportalbackend.ai.enums.AiResourceScope;
import com.web.labportalbackend.ai.enums.AiResourceType;
import com.web.labportalbackend.ai.enums.AiToolArgument;
import com.web.labportalbackend.ai.enums.AiToolId;
import com.web.labportalbackend.ai.service.AiAssistantProfile;
import com.web.labportalbackend.ai.service.AiAssistantRegistry;
import com.web.labportalbackend.ai.service.AiAuthorizedToolPolicy;
import com.web.labportalbackend.ai.service.AiCapabilityDecision;
import com.web.labportalbackend.ai.service.AiToolDefinition;
import com.web.labportalbackend.ai.service.AiToolPolicyDeniedException;
import com.web.labportalbackend.ai.service.AiToolPolicyDenialReason;
import com.web.labportalbackend.ai.service.AiToolRequest;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class AiToolPolicyResolverImplTest {

    private static final Set<AiToolArgument> RESOURCE = Set.of(AiToolArgument.RESOURCE);
    private static final Set<AiToolArgument> RESOURCE_AND_PARENT = Set.of(
            AiToolArgument.RESOURCE, AiToolArgument.PARENT_RESOURCE);
    /** Independent contract table: it must not be derived from the production catalog under test. */
    private static final List<ExpectedTool> EXPECTED_TOOLS = List.of(
            expected(AiToolId.ADMIN_SYSTEM_SUMMARY, AiCapability.ADMIN_SYSTEM_SUMMARY, AiAssistantKey.ADMIN_ASSISTANT,
                    AiAssistantDomain.ADMIN, AiAssistantToolGroup.ADMIN_READ, AiResourceType.SYSTEM, null,
                    AiRequestedAction.READ, AiActionRiskBoundary.READ_ONLY, RESOURCE),
            expected(AiToolId.ADMIN_AUDIT_SUMMARY, AiCapability.ADMIN_AUDIT_SUMMARY, AiAssistantKey.ADMIN_ASSISTANT,
                    AiAssistantDomain.ADMIN, AiAssistantToolGroup.ADMIN_READ, AiResourceType.AUDIT_LOG, null,
                    AiRequestedAction.READ, AiActionRiskBoundary.READ_ONLY, RESOURCE),
            expected(AiToolId.ADMIN_USER_STATUS_LOOKUP, AiCapability.ADMIN_USER_STATUS_LOOKUP, AiAssistantKey.ADMIN_ASSISTANT,
                    AiAssistantDomain.ADMIN, AiAssistantToolGroup.ADMIN_READ, AiResourceType.USER_ACCOUNT, null,
                    AiRequestedAction.READ, AiActionRiskBoundary.READ_ONLY, RESOURCE),
            expected(AiToolId.ADMIN_CONFIG_DRAFT, AiCapability.ADMIN_CONFIG_DRAFT, AiAssistantKey.ADMIN_ASSISTANT,
                    AiAssistantDomain.ADMIN, AiAssistantToolGroup.ADMIN_DRAFT, AiResourceType.SYSTEM_CONFIG, null,
                    AiRequestedAction.DRAFT, AiActionRiskBoundary.DRAFT_ONLY, RESOURCE),
            expected(AiToolId.ADMIN_ACCOUNT_ACTION_DRAFT, AiCapability.ADMIN_ACCOUNT_ACTION_DRAFT, AiAssistantKey.ADMIN_ASSISTANT,
                    AiAssistantDomain.ADMIN, AiAssistantToolGroup.ADMIN_DRAFT, AiResourceType.USER_ACCOUNT, null,
                    AiRequestedAction.DRAFT, AiActionRiskBoundary.DRAFT_ONLY, RESOURCE),
            expected(AiToolId.LAB_POLICY_READ, AiCapability.LAB_POLICY_READ, AiAssistantKey.LAB_ASSISTANT,
                    AiAssistantDomain.LAB, AiAssistantToolGroup.LAB_READ, AiResourceType.LABORATORY, null,
                    AiRequestedAction.READ, AiActionRiskBoundary.READ_ONLY, RESOURCE),
            expected(AiToolId.LAB_SLOT_READ, AiCapability.LAB_SLOT_READ, AiAssistantKey.LAB_ASSISTANT,
                    AiAssistantDomain.LAB, AiAssistantToolGroup.LAB_READ, AiResourceType.TIME_SLOT, null,
                    AiRequestedAction.READ, AiActionRiskBoundary.READ_ONLY, RESOURCE),
            expected(AiToolId.LAB_OWN_BOOKING_READ, AiCapability.LAB_OWN_BOOKING_READ, AiAssistantKey.LAB_ASSISTANT,
                    AiAssistantDomain.LAB, AiAssistantToolGroup.LAB_READ, AiResourceType.BOOKING, null,
                    AiRequestedAction.READ, AiActionRiskBoundary.READ_ONLY, RESOURCE),
            expected(AiToolId.LAB_MANAGED_SUMMARY, AiCapability.LAB_MANAGED_SUMMARY, AiAssistantKey.LAB_ASSISTANT,
                    AiAssistantDomain.LAB, AiAssistantToolGroup.LAB_READ, AiResourceType.LABORATORY, null,
                    AiRequestedAction.READ, AiActionRiskBoundary.READ_ONLY, RESOURCE),
            expected(AiToolId.LAB_BOOKING_DRAFT, AiCapability.LAB_BOOKING_DRAFT, AiAssistantKey.LAB_ASSISTANT,
                    AiAssistantDomain.LAB, AiAssistantToolGroup.LAB_DRAFT, AiResourceType.TIME_SLOT, null,
                    AiRequestedAction.DRAFT, AiActionRiskBoundary.DRAFT_ONLY, RESOURCE),
            expected(AiToolId.LAB_CHECKIN_GUIDANCE, AiCapability.LAB_CHECKIN_GUIDANCE, AiAssistantKey.LAB_ASSISTANT,
                    AiAssistantDomain.LAB, AiAssistantToolGroup.LAB_READ, AiResourceType.BOOKING, null,
                    AiRequestedAction.READ, AiActionRiskBoundary.READ_ONLY, RESOURCE),
            expected(AiToolId.RESEARCH_PROJECT_SUMMARY, AiCapability.RESEARCH_PROJECT_SUMMARY,
                    AiAssistantKey.RESEARCH_ASSISTANT, AiAssistantDomain.RESEARCH, AiAssistantToolGroup.RESEARCH_READ,
                    AiResourceType.PROJECT, null, AiRequestedAction.READ, AiActionRiskBoundary.READ_ONLY, RESOURCE),
            expected(AiToolId.RESEARCH_GROUP_SUMMARY, AiCapability.RESEARCH_GROUP_SUMMARY,
                    AiAssistantKey.RESEARCH_ASSISTANT, AiAssistantDomain.RESEARCH, AiAssistantToolGroup.RESEARCH_READ,
                    AiResourceType.GROUP, null, AiRequestedAction.READ, AiActionRiskBoundary.READ_ONLY, RESOURCE),
            expected(AiToolId.RESEARCH_ASSIGNED_TASK_READ, AiCapability.RESEARCH_ASSIGNED_TASK_READ,
                    AiAssistantKey.RESEARCH_ASSISTANT, AiAssistantDomain.RESEARCH, AiAssistantToolGroup.RESEARCH_READ,
                    AiResourceType.TASK, null, AiRequestedAction.READ, AiActionRiskBoundary.READ_ONLY, RESOURCE),
            expected(AiToolId.RESEARCH_TASK_PROPOSAL_DRAFT, AiCapability.RESEARCH_TASK_PROPOSAL_DRAFT,
                    AiAssistantKey.RESEARCH_ASSISTANT, AiAssistantDomain.RESEARCH, AiAssistantToolGroup.RESEARCH_DRAFT,
                    AiResourceType.GROUP, AiResourceType.PROJECT, AiRequestedAction.DRAFT, AiActionRiskBoundary.DRAFT_ONLY,
                    RESOURCE_AND_PARENT),
            expected(AiToolId.RESEARCH_TASK_SUGGESTION_DRAFT, AiCapability.RESEARCH_TASK_SUGGESTION_DRAFT,
                    AiAssistantKey.RESEARCH_ASSISTANT, AiAssistantDomain.RESEARCH, AiAssistantToolGroup.RESEARCH_DRAFT,
                    AiResourceType.TASK, null, AiRequestedAction.DRAFT, AiActionRiskBoundary.DRAFT_ONLY, RESOURCE),
            expected(AiToolId.RESEARCH_REPORT_REVIEW_DRAFT, AiCapability.RESEARCH_REPORT_REVIEW_DRAFT,
                    AiAssistantKey.RESEARCH_ASSISTANT, AiAssistantDomain.RESEARCH, AiAssistantToolGroup.RESEARCH_DRAFT,
                    AiResourceType.REPORT, null, AiRequestedAction.DRAFT, AiActionRiskBoundary.DRAFT_ONLY, RESOURCE));

    static Stream<ExpectedTool> expectedTools() {
        return EXPECTED_TOOLS.stream();
    }

    static Stream<ExpectedTool> toolsWithoutParentArgument() {
        return expectedTools().filter(expected -> expected.parentResourceType() == null);
    }

    static Stream<ExpectedTool> nonGlobalExpectedTools() {
        return expectedTools().filter(expected -> !global(expected.resourceType()));
    }

    static Stream<ExpectedTool> globalExpectedTools() {
        return expectedTools().filter(expected -> global(expected.resourceType()));
    }

    @Test
    void independentExpectedTableCoversEveryFixedIdAndCapabilityExactlyOnce() {
        assertEquals(Set.of(AiToolId.values()), EXPECTED_TOOLS.stream().map(ExpectedTool::id).collect(java.util.stream.Collectors.toSet()));
        assertEquals(Set.of(AiCapability.values()), EXPECTED_TOOLS.stream().map(ExpectedTool::capability)
                .collect(java.util.stream.Collectors.toSet()));
    }

    @ParameterizedTest
    @MethodSource("expectedTools")
    void projectsEveryCanonicalRowToItsExactDescriptor(ExpectedTool expected) {
        AiToolDefinition definition = definition(expected);
        AiAuthorizedToolPolicy policy = resolver(expected.key(), profile(expected, expected.group(), true))
                .resolve(decision(expected), request(expected));

        assertEquals(expected.id(), policy.descriptor().id());
        assertEquals(expected.capability(), policy.descriptor().capability());
        assertEquals(expected.key().domain(), policy.descriptor().domain());
        assertEquals(expected.group(), policy.descriptor().group());
        assertEquals(expected.resourceType(), policy.descriptor().resourceType());
        assertEquals(expected.parentResourceType(), policy.descriptor().parentResourceType());
        assertEquals(expected.action(), policy.descriptor().action());
        assertEquals(expected.riskBoundary(), policy.descriptor().riskBoundary());
        assertEquals(AiToolRegistryServiceImpl.SCHEMA_VERSION, policy.descriptor().schemaVersion());
        assertEquals(expected.arguments(), policy.descriptor().arguments());
    }

    @Test
    void rejectsOtherwiseCanonicalAllowedDecisionWithDeniedReason() {
        ExpectedTool expected = EXPECTED_TOOLS.stream()
                .filter(tool -> tool.capability() == AiCapability.LAB_POLICY_READ).findFirst().orElseThrow();
        AiCapabilityDecision valid = decision(expected);
        AiCapabilityDecision deniedReason = new AiCapabilityDecision(true, valid.acceptedActorId(), valid.selectedSystemRole(),
                valid.assistantKey(), valid.domain(), valid.capability(), valid.resolvedResource(),
                AiCapabilityDecisionReason.DENIED_BY_REQUEST, null, valid.riskBoundary(), Set.of(),
                valid.checkinGuidancePolicySnapshot());

        assertDenied(AiToolPolicyDenialReason.MALFORMED_DECISION,
                () -> resolver(expected.key(), profile(expected, expected.group(), true)).resolve(deniedReason, request(expected)));
    }

    @ParameterizedTest
    @MethodSource("expectedTools")
    void rejectsKeyProfileDescriptorResourceActionRiskSchemaAndMissingArgumentMismatchesForEveryRow(ExpectedTool expected) {
        AiToolDefinition definition = definition(expected);
        AiCapabilityDecision allowed = decision(expected);
        AiToolPolicyResolverImpl resolver = resolver(expected.key(), profile(expected, expected.group(), true));

        assertDenied(AiToolPolicyDenialReason.MALFORMED_DECISION,
                () -> resolver.resolve(decisionWithKey(expected, differentKey(expected.key())), request(expected)));
        assertDenied(AiToolPolicyDenialReason.PROFILE_MISCONFIGURED,
                () -> resolver(expected.key(), foreignProfile(expected)).resolve(allowed, request(expected)));
        assertDenied(AiToolPolicyDenialReason.TOOL_GROUP_NOT_ALLOWED,
                () -> resolver(expected.key(), profile(expected, alternateGroup(expected.group()), true)).resolve(allowed, request(expected)));
        assertDenied(AiToolPolicyDenialReason.TOOL_CAPABILITY_MISMATCH,
                () -> resolver.resolve(allowed, request(otherWithDifferentCapability(expected))));
        assertDenied(AiToolPolicyDenialReason.TOOL_CAPABILITY_MISMATCH,
                () -> resolver.resolve(allowed, request(otherWithDifferentAction(expected))));
        assertDenied(AiToolPolicyDenialReason.RESOURCE_MISMATCH,
                () -> resolver.resolve(invalidResourceDecision(expected), request(expected)));
        assertDenied(AiToolPolicyDenialReason.MALFORMED_DECISION,
                () -> resolver.resolve(wrongRiskDecision(expected), request(expected)));
        assertDenied(AiToolPolicyDenialReason.SCHEMA_MISMATCH,
                () -> resolver.resolve(allowed, new AiToolRequest(expected.id(), "wrong-schema", expected.arguments())));
        assertDenied(AiToolPolicyDenialReason.ARGUMENT_MISMATCH,
                () -> resolver.resolve(allowed, new AiToolRequest(expected.id(), AiToolRegistryServiceImpl.SCHEMA_VERSION,
                        missingArguments(expected))));
    }

    @ParameterizedTest
    @MethodSource("toolsWithoutParentArgument")
    void rejectsExtraArgumentsForEveryRowWhoseFixedArgumentSetCanBeExpanded(ExpectedTool expected) {
        AiToolPolicyResolverImpl resolver = resolver(expected.key(), profile(expected, expected.group(), true));
        assertDenied(AiToolPolicyDenialReason.ARGUMENT_MISMATCH,
                () -> resolver.resolve(decision(expected), new AiToolRequest(expected.id(), AiToolRegistryServiceImpl.SCHEMA_VERSION,
                        RESOURCE_AND_PARENT)));
    }

    @ParameterizedTest
    @MethodSource("nonGlobalExpectedTools")
    void rejectsEveryMalformedIdentityVariantForEveryNonGlobalCatalogRow(ExpectedTool expected) {
        AiToolPolicyResolverImpl resolver = resolver(expected.key(), profile(expected, expected.group(), true));

        invalidIdentityMutations(decision(expected).resolvedResource()).forEach(resource -> assertDenied(
                AiToolPolicyDenialReason.RESOURCE_MISMATCH,
                () -> resolver.resolve(decisionWithResource(expected, resource), request(expected))));
    }

    @ParameterizedTest
    @MethodSource("globalExpectedTools")
    void rejectsEveryUnexpectedBusinessIdentityForGlobalCatalogRows(ExpectedTool expected) {
        AiToolPolicyResolverImpl resolver = resolver(expected.key(), profile(expected, expected.group(), true));

        invalidGlobalIdentityMutations(expected.resourceType()).forEach(resource -> assertDenied(
                AiToolPolicyDenialReason.RESOURCE_MISMATCH,
                () -> resolver.resolve(decisionWithResource(expected, resource), request(expected))));
    }

    @Test
    void rejectsBothMissingAndNonMatchingParentIdentityForTheParentedCatalogRow() {
        ExpectedTool expected = EXPECTED_TOOLS.stream()
                .filter(tool -> tool.capability() == AiCapability.RESEARCH_TASK_PROPOSAL_DRAFT).findFirst().orElseThrow();
        AiToolPolicyResolverImpl resolver = resolver(expected.key(), profile(expected, expected.group(), true));

        assertDenied(AiToolPolicyDenialReason.RESOURCE_MISMATCH,
                () -> resolver.resolve(parentMismatchDecision(expected, null, 10L), request(expected)));
        assertDenied(AiToolPolicyDenialReason.RESOURCE_MISMATCH,
                () -> resolver.resolve(parentMismatchDecision(expected, 20L, 99L), request(expected)));
    }

    @ParameterizedTest
    @MethodSource("expectedTools")
    void deniesDisabledAdminCatalogBeforeProjection(ExpectedTool expected) {
        if (expected.domain() != AiAssistantDomain.ADMIN) {
            return;
        }
        assertDenied(AiToolPolicyDenialReason.PROFILE_DISABLED,
                () -> resolver(expected.key(), profile(expected, expected.group(), false)).resolve(decision(expected), request(expected)));
    }

    private static ExpectedTool expected(AiToolId id, AiCapability capability, AiAssistantKey key, AiAssistantDomain domain,
                                         AiAssistantToolGroup group, AiResourceType resourceType, AiResourceType parentResourceType,
                                         AiRequestedAction action, AiActionRiskBoundary riskBoundary, Set<AiToolArgument> arguments) {
        return new ExpectedTool(id, capability, key, domain, group, resourceType, parentResourceType, action, riskBoundary, arguments);
    }

    private static AiToolDefinition definition(ExpectedTool expected) {
        AiToolDefinition definition = new AiToolRegistryServiceImpl().get(expected.id());
        assertEquals(expected.capability(), definition.capability());
        return definition;
    }

    private static AiToolPolicyResolverImpl resolver(AiAssistantKey lookupKey, AiAssistantProfile returnedProfile) {
        AiAssistantRegistry profiles = mock(AiAssistantRegistry.class);
        when(profiles.getProfile(lookupKey)).thenReturn(returnedProfile);
        return new AiToolPolicyResolverImpl(new AiToolRegistryServiceImpl(), profiles);
    }

    private static AiToolRequest request(ExpectedTool expected) {
        return new AiToolRequest(expected.id(), AiToolRegistryServiceImpl.SCHEMA_VERSION, expected.arguments());
    }

    private static AiCapabilityDecision decision(ExpectedTool expected) {
        return new AiCapabilityDecision(true, 7L, AiAssistantSystemRole.STUDENT, expected.key(), expected.domain(),
                expected.capability(), canonicalResource(expected.resourceType()),
                AiCapabilityDecisionReason.ALLOWED_BY_EFFECTIVE_PERMISSION, null, expected.riskBoundary(), Set.of(),
                expected.capability() == AiCapability.LAB_CHECKIN_GUIDANCE
                        ? new AiCapabilityDecision.CheckinGuidancePolicySnapshot(Instant.EPOCH) : null);
    }

    private static AiCapabilityDecision.ResolvedResource canonicalResource(AiResourceType type) {
        return switch (type) {
            case SYSTEM, AUDIT_LOG, SYSTEM_CONFIG -> new AiCapabilityDecision.ResolvedResource(type, null, null,
                    null, null, null, AiResourceScope.GLOBAL);
            case USER_ACCOUNT -> resource(type, 10L, null, null, null, null);
            case LABORATORY -> resource(type, 10L, 10L, null, null, null);
            case TIME_SLOT, BOOKING -> resource(type, 10L, 1L, null, null, null);
            case PROJECT -> resource(type, 20L, 1L, 20L, null, null);
            case GROUP -> resource(type, 30L, 1L, 20L, 30L, null);
            case TASK -> resource(type, 40L, 1L, 20L, 30L, 40L);
            case REPORT -> resource(type, 50L, 1L, 20L, 30L, 40L);
        };
    }

    private static AiCapabilityDecision.ResolvedResource resource(AiResourceType type, Long id, Long labId,
                                                                    Long projectId, Long groupId, Long taskId) {
        return new AiCapabilityDecision.ResolvedResource(type, id, labId, projectId, groupId, taskId,
                AiResourceScope.EXISTING_BUSINESS_PERMISSION);
    }

    private static AiCapabilityDecision decisionWithKey(ExpectedTool expected, AiAssistantKey key) {
        AiCapabilityDecision valid = decision(expected);
        return new AiCapabilityDecision(true, valid.acceptedActorId(), valid.selectedSystemRole(), key, valid.domain(),
                valid.capability(), valid.resolvedResource(), valid.decisionReason(), null, valid.riskBoundary(), Set.of(),
                valid.checkinGuidancePolicySnapshot());
    }

    private static AiCapabilityDecision invalidResourceDecision(ExpectedTool expected) {
        AiCapabilityDecision valid = decision(expected);
        AiCapabilityDecision.ResolvedResource resource = valid.resolvedResource();
        Long invalidId = global(expected.resourceType()) ? null : 0L;
        Long invalidLabId = global(expected.resourceType()) ? Long.valueOf(99L) : resource.labId();
        return new AiCapabilityDecision(true, valid.acceptedActorId(), valid.selectedSystemRole(), valid.assistantKey(),
                valid.domain(), valid.capability(), new AiCapabilityDecision.ResolvedResource(resource.type(),
                invalidId, invalidLabId,
                resource.projectId(), resource.groupId(), resource.taskId(), resource.effectiveScope()), valid.decisionReason(),
                null, valid.riskBoundary(), Set.of(), valid.checkinGuidancePolicySnapshot());
    }

    private static AiCapabilityDecision decisionWithResource(ExpectedTool expected,
                                                              AiCapabilityDecision.ResolvedResource resource) {
        AiCapabilityDecision valid = decision(expected);
        return new AiCapabilityDecision(true, valid.acceptedActorId(), valid.selectedSystemRole(), valid.assistantKey(),
                valid.domain(), valid.capability(), resource, valid.decisionReason(), null, valid.riskBoundary(), Set.of(),
                valid.checkinGuidancePolicySnapshot());
    }

    private static List<AiCapabilityDecision.ResolvedResource> invalidIdentityMutations(
            AiCapabilityDecision.ResolvedResource resource) {
        Long id = resource.id();
        Long labId = resource.labId();
        Long projectId = resource.projectId();
        Long groupId = resource.groupId();
        Long taskId = resource.taskId();
        return switch (resource.type()) {
            case USER_ACCOUNT -> List.of(resource(resource.type(), 0L, null, null, null, null),
                    resource(resource.type(), id, 1L, null, null, null));
            case LABORATORY -> List.of(resource(resource.type(), id, id + 1L, null, null, null),
                    resource(resource.type(), id, null, null, null, null));
            case TIME_SLOT, BOOKING -> List.of(resource(resource.type(), id, null, null, null, null),
                    resource(resource.type(), id, labId, 1L, null, null));
            case PROJECT -> List.of(resource(resource.type(), id, labId, id + 1L, null, null),
                    resource(resource.type(), id, null, projectId, null, null));
            case GROUP -> List.of(resource(resource.type(), id, labId, projectId, id + 1L, null),
                    resource(resource.type(), id, labId, null, groupId, null));
            case TASK -> List.of(resource(resource.type(), id, labId, projectId, groupId, id + 1L),
                    resource(resource.type(), id, labId, projectId, null, taskId));
            case REPORT -> List.of(resource(resource.type(), 0L, labId, projectId, groupId, taskId),
                    resource(resource.type(), id, labId, null, groupId, taskId),
                    resource(resource.type(), id, labId, projectId, groupId, 0L));
            case SYSTEM, AUDIT_LOG, SYSTEM_CONFIG -> throw new IllegalArgumentException("global resource is excluded");
        };
    }

    private static List<AiCapabilityDecision.ResolvedResource> invalidGlobalIdentityMutations(AiResourceType type) {
        return List.of(
                new AiCapabilityDecision.ResolvedResource(type, null, null, 20L, null, null, AiResourceScope.GLOBAL),
                new AiCapabilityDecision.ResolvedResource(type, null, null, null, 30L, null, AiResourceScope.GLOBAL),
                new AiCapabilityDecision.ResolvedResource(type, null, null, null, null, 40L, AiResourceScope.GLOBAL));
    }

    private static AiCapabilityDecision parentMismatchDecision(ExpectedTool expected, Long projectId, Long groupId) {
        AiCapabilityDecision valid = decision(expected);
        AiCapabilityDecision.ResolvedResource resource = valid.resolvedResource();
        return new AiCapabilityDecision(true, valid.acceptedActorId(), valid.selectedSystemRole(), valid.assistantKey(),
                valid.domain(), valid.capability(), new AiCapabilityDecision.ResolvedResource(resource.type(), resource.id(),
                resource.labId(), projectId, groupId, resource.taskId(), resource.effectiveScope()), valid.decisionReason(), null,
                valid.riskBoundary(), Set.of(), valid.checkinGuidancePolicySnapshot());
    }

    private static AiCapabilityDecision wrongRiskDecision(ExpectedTool expected) {
        AiCapabilityDecision valid = decision(expected);
        AiActionRiskBoundary wrong = valid.riskBoundary() == AiActionRiskBoundary.READ_ONLY
                ? AiActionRiskBoundary.DRAFT_ONLY : AiActionRiskBoundary.READ_ONLY;
        return new AiCapabilityDecision(true, valid.acceptedActorId(), valid.selectedSystemRole(), valid.assistantKey(),
                valid.domain(), valid.capability(), valid.resolvedResource(), valid.decisionReason(), null, wrong, Set.of(),
                valid.checkinGuidancePolicySnapshot());
    }

    private static AiAssistantProfile profile(ExpectedTool expected, AiAssistantToolGroup group, boolean enabled) {
        return new AiAssistantProfile(expected.key(), expected.domain(), enabled, Set.of(AiAssistantSystemRole.STUDENT),
                "profile", "prompt-v1", null, "namespace", AiQuotaPolicyReference.AI_CONFIG_QUOTA, Set.of(group), "suite-v1");
    }

    private static AiAssistantProfile foreignProfile(ExpectedTool expected) {
        ExpectedTool foreign = EXPECTED_TOOLS.stream().filter(candidate -> candidate.key() != expected.key()).findFirst().orElseThrow();
        return profile(foreign, foreign.group(), true);
    }

    private static ExpectedTool otherWithDifferentCapability(ExpectedTool expected) {
        return EXPECTED_TOOLS.stream().filter(candidate -> candidate.capability() != expected.capability()).findFirst().orElseThrow();
    }

    private static ExpectedTool otherWithDifferentAction(ExpectedTool expected) {
        return EXPECTED_TOOLS.stream().filter(candidate -> candidate.action() != expected.action()).findFirst().orElseThrow();
    }

    private static AiAssistantKey differentKey(AiAssistantKey key) {
        return key == AiAssistantKey.ADMIN_ASSISTANT ? AiAssistantKey.LAB_ASSISTANT : AiAssistantKey.ADMIN_ASSISTANT;
    }

    private static AiAssistantToolGroup alternateGroup(AiAssistantToolGroup group) {
        return switch (group) {
            case LAB_READ -> AiAssistantToolGroup.LAB_DRAFT;
            case LAB_DRAFT -> AiAssistantToolGroup.LAB_READ;
            case RESEARCH_READ -> AiAssistantToolGroup.RESEARCH_DRAFT;
            case RESEARCH_DRAFT -> AiAssistantToolGroup.RESEARCH_READ;
            case ADMIN_READ -> AiAssistantToolGroup.ADMIN_DRAFT;
            case ADMIN_DRAFT -> AiAssistantToolGroup.ADMIN_READ;
        };
    }

    private static Set<AiToolArgument> missingArguments(ExpectedTool expected) {
        return expected.arguments().contains(AiToolArgument.PARENT_RESOURCE) ? RESOURCE : Set.of();
    }

    private static boolean global(AiResourceType type) {
        return type == AiResourceType.SYSTEM || type == AiResourceType.AUDIT_LOG || type == AiResourceType.SYSTEM_CONFIG;
    }

    private static void assertDenied(AiToolPolicyDenialReason reason, Executable executable) {
        assertEquals(reason, assertThrows(AiToolPolicyDeniedException.class, executable).reason());
    }

    private record ExpectedTool(AiToolId id, AiCapability capability, AiAssistantKey key, AiAssistantDomain domain,
                                AiAssistantToolGroup group, AiResourceType resourceType, AiResourceType parentResourceType,
                                AiRequestedAction action, AiActionRiskBoundary riskBoundary, Set<AiToolArgument> arguments) {
    }
}
