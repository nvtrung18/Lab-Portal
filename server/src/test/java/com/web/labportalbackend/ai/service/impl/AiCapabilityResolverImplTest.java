package com.web.labportalbackend.ai.service.impl;

import static com.web.labportalbackend.ai.enums.AiCapabilityDenialReason.ACTOR_MISMATCH;
import static com.web.labportalbackend.ai.enums.AiCapabilityDenialReason.ACTOR_UNAVAILABLE;
import static com.web.labportalbackend.ai.enums.AiCapabilityDenialReason.ACTION_NOT_ALLOWED;
import static com.web.labportalbackend.ai.enums.AiCapabilityDenialReason.ASSISTANT_DISABLED;
import static com.web.labportalbackend.ai.enums.AiCapabilityDenialReason.ASSISTANT_MISCONFIGURED;
import static com.web.labportalbackend.ai.enums.AiCapabilityDenialReason.DOMAIN_MISMATCH;
import static com.web.labportalbackend.ai.enums.AiCapabilityDenialReason.MALFORMED_REQUEST;
import static com.web.labportalbackend.ai.enums.AiCapabilityDenialReason.PROHIBITED_ACTION;
import static com.web.labportalbackend.ai.enums.AiCapabilityDenialReason.RESOURCE_REQUIRED;
import static com.web.labportalbackend.ai.enums.AiCapabilityDenialReason.RESOURCE_TYPE_MISMATCH;
import static com.web.labportalbackend.ai.enums.AiCapabilityDenialReason.RESOURCE_UNAVAILABLE;
import static com.web.labportalbackend.ai.enums.AiCapabilityDenialReason.ROLE_NOT_ALLOWED;
import static com.web.labportalbackend.ai.enums.AiCapabilityDenialReason.UNAUTHENTICATED;
import static com.web.labportalbackend.ai.enums.AiCapabilityDenialReason.UNKNOWN_ASSISTANT;
import static com.web.labportalbackend.ai.enums.AiCapabilityDenialReason.UNKNOWN_CAPABILITY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.web.labportalbackend.ai.enums.AiActionRiskBoundary;
import com.web.labportalbackend.ai.enums.AiAssistantDomain;
import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.ai.enums.AiAssistantSystemRole;
import com.web.labportalbackend.ai.enums.AiAssistantToolGroup;
import com.web.labportalbackend.ai.enums.AiCapability;
import com.web.labportalbackend.ai.enums.AiCapabilityEvidence;
import com.web.labportalbackend.ai.enums.AiRequestedAction;
import com.web.labportalbackend.ai.enums.AiResourceScope;
import com.web.labportalbackend.ai.enums.AiResourceType;
import com.web.labportalbackend.ai.enums.AiQuotaPolicyReference;
import com.web.labportalbackend.ai.repository.AiAssistantConfigRepository;
import com.web.labportalbackend.ai.security.AiCapabilityPermissionAdapter;
import com.web.labportalbackend.ai.service.AiAssistantProfile;
import com.web.labportalbackend.ai.service.AiAssistantRegistry;
import com.web.labportalbackend.ai.service.AiAssistantRegistryException;
import com.web.labportalbackend.ai.service.AiAssistantRegistryFailure;
import com.web.labportalbackend.ai.service.AiCapabilityDecision;
import com.web.labportalbackend.ai.service.AiCapabilityDeniedException;
import com.web.labportalbackend.ai.service.AiCapabilityRequest;
import com.web.labportalbackend.auth.entity.Role;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.enums.UserStatus;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AiCapabilityResolverImplTest {

    @Mock AiAssistantRegistry registry;
    @Mock UserRepository userRepository;
    @Mock AiCapabilityPermissionAdapter adminAdapter;
    @Mock AiCapabilityPermissionAdapter labAdapter;
    @Mock AiCapabilityPermissionAdapter researchAdapter;

    private AiCapabilityResolverImpl resolver;

    @BeforeEach
    void setUp() {
        when(adminAdapter.domain()).thenReturn(AiAssistantDomain.ADMIN);
        when(labAdapter.domain()).thenReturn(AiAssistantDomain.LAB);
        when(researchAdapter.domain()).thenReturn(AiAssistantDomain.RESEARCH);
        resolver = new AiCapabilityResolverImpl(registry, userRepository,
                List.of(adminAdapter, labAdapter, researchAdapter));
        clearInvocations(adminAdapter, labAdapter, researchAdapter);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void deniesActorMismatchBeforeRegistryOrResourceReads() {
        authenticate(activeUser(7L, "student", "STUDENT"));

        AiCapabilityDecision decision = resolver.resolve(request(
                AiAssistantKey.LAB_ASSISTANT, 8L, AiCapability.LAB_POLICY_READ,
                AiResourceType.LABORATORY, 10L, AiRequestedAction.READ));

        assertDenied(decision, ACTOR_MISMATCH);
        verifyNoInteractions(registry);
        verifyNoInteractions(adminAdapter, labAdapter, researchAdapter);
    }

    static Stream<Arguments> malformedRequests() {
        AiCapabilityRequest.ResourceReference lab = resource(AiResourceType.LABORATORY, 10L);
        AiCapabilityRequest.ResourceReference project = resource(AiResourceType.PROJECT, 20L);
        return Stream.of(
                Arguments.of(null, MALFORMED_REQUEST),
                Arguments.of(raw(null, 7L, AiCapability.LAB_POLICY_READ, lab, null,
                        AiRequestedAction.READ), UNKNOWN_ASSISTANT),
                Arguments.of(raw(AiAssistantKey.LAB_ASSISTANT, 7L, null, lab, null,
                        AiRequestedAction.READ), UNKNOWN_CAPABILITY),
                Arguments.of(raw(AiAssistantKey.LAB_ASSISTANT, null, AiCapability.LAB_POLICY_READ, lab, null,
                        AiRequestedAction.READ), MALFORMED_REQUEST),
                Arguments.of(raw(AiAssistantKey.LAB_ASSISTANT, 0L, AiCapability.LAB_POLICY_READ, lab, null,
                        AiRequestedAction.READ), MALFORMED_REQUEST),
                Arguments.of(raw(AiAssistantKey.LAB_ASSISTANT, 7L, AiCapability.LAB_POLICY_READ, null, null,
                        AiRequestedAction.READ), RESOURCE_REQUIRED),
                Arguments.of(raw(AiAssistantKey.LAB_ASSISTANT, 7L, AiCapability.LAB_POLICY_READ,
                        resource(AiResourceType.TIME_SLOT, 10L), null, AiRequestedAction.READ),
                        RESOURCE_TYPE_MISMATCH),
                Arguments.of(raw(AiAssistantKey.ADMIN_ASSISTANT, 1L, AiCapability.ADMIN_SYSTEM_SUMMARY,
                        resource(AiResourceType.SYSTEM, 1L), null, AiRequestedAction.READ), MALFORMED_REQUEST),
                Arguments.of(raw(AiAssistantKey.LAB_ASSISTANT, 7L, AiCapability.LAB_POLICY_READ,
                        resource(AiResourceType.LABORATORY, null), null, AiRequestedAction.READ), MALFORMED_REQUEST),
                Arguments.of(raw(AiAssistantKey.RESEARCH_ASSISTANT, 7L,
                        AiCapability.RESEARCH_TASK_PROPOSAL_DRAFT, resource(AiResourceType.GROUP, 30L), null,
                        AiRequestedAction.DRAFT), RESOURCE_TYPE_MISMATCH),
                Arguments.of(raw(AiAssistantKey.RESEARCH_ASSISTANT, 7L,
                        AiCapability.RESEARCH_TASK_PROPOSAL_DRAFT, resource(AiResourceType.GROUP, 30L),
                        resource(AiResourceType.LABORATORY, 20L), AiRequestedAction.DRAFT),
                        RESOURCE_TYPE_MISMATCH),
                Arguments.of(raw(AiAssistantKey.RESEARCH_ASSISTANT, 7L,
                        AiCapability.RESEARCH_TASK_PROPOSAL_DRAFT, resource(AiResourceType.GROUP, 30L),
                        resource(AiResourceType.PROJECT, 0L), AiRequestedAction.DRAFT),
                        RESOURCE_TYPE_MISMATCH),
                Arguments.of(raw(AiAssistantKey.LAB_ASSISTANT, 7L, AiCapability.LAB_POLICY_READ, lab, project,
                        AiRequestedAction.READ), RESOURCE_TYPE_MISMATCH),
                Arguments.of(raw(AiAssistantKey.LAB_ASSISTANT, 7L, AiCapability.LAB_POLICY_READ, lab, null,
                        AiRequestedAction.MUTATION), PROHIBITED_ACTION),
                Arguments.of(raw(AiAssistantKey.LAB_ASSISTANT, 7L, AiCapability.LAB_POLICY_READ, lab, null,
                        null), ACTION_NOT_ALLOWED),
                Arguments.of(raw(AiAssistantKey.LAB_ASSISTANT, 7L, AiCapability.LAB_POLICY_READ, lab, null,
                        AiRequestedAction.DRAFT), ACTION_NOT_ALLOWED));
    }

    @ParameterizedTest(name = "malformed request {index} denies as {1} before authentication and reads")
    @MethodSource("malformedRequests")
    void malformedRequestMatrixIsRedactedAndShortCircuits(AiCapabilityRequest request,
                                                           com.web.labportalbackend.ai.enums.AiCapabilityDenialReason reason) {
        AiCapabilityDecision decision = resolver.resolve(request);

        assertDenied(decision, reason);
        verifyNoInteractions(userRepository, registry, adminAdapter, labAdapter, researchAdapter);
    }

    @Test
    void unauthenticatedRequestIsRedactedBeforeActorRegistryOrResourceReads() {
        AiCapabilityDecision decision = resolver.resolve(request(
                AiAssistantKey.LAB_ASSISTANT, 7L, AiCapability.LAB_POLICY_READ,
                AiResourceType.LABORATORY, 10L, AiRequestedAction.READ));

        assertDenied(decision, UNAUTHENTICATED);
        verifyNoInteractions(userRepository, registry, adminAdapter, labAdapter, researchAdapter);
    }

    @Test
    void mapsUnchangedRealRegistryAdminDisablementBeforeAdminAdapter() {
        AiAssistantConfigRepository configRepository = mock(AiAssistantConfigRepository.class);
        AiCapabilityResolverImpl realRegistryResolver = new AiCapabilityResolverImpl(
                new AiAssistantRegistryServiceImpl(configRepository), userRepository,
                List.of(adminAdapter, labAdapter, researchAdapter));
        User admin = activeUser(1L, "admin", "ADMIN");
        authenticate(admin);

        AiCapabilityDecision decision = realRegistryResolver.resolve(request(
                AiAssistantKey.ADMIN_ASSISTANT, 1L, AiCapability.ADMIN_SYSTEM_SUMMARY,
                AiResourceType.SYSTEM, null, AiRequestedAction.READ));

        assertDenied(decision, ASSISTANT_DISABLED);
        verifyNoInteractions(configRepository);
        verify(adminAdapter, never()).evaluate(admin, request(
                AiAssistantKey.ADMIN_ASSISTANT, 1L, AiCapability.ADMIN_SYSTEM_SUMMARY,
                AiResourceType.SYSTEM, null, AiRequestedAction.READ));
    }

    static Stream<Arguments> crossDomainRequests() {
        return Stream.of(
                Arguments.of(AiAssistantKey.LAB_ASSISTANT, AiAssistantDomain.LAB,
                        AiAssistantSystemRole.STUDENT, "STUDENT", AiCapability.RESEARCH_TASK_SUGGESTION_DRAFT,
                        AiResourceType.TASK, 99L, AiRequestedAction.DRAFT),
                Arguments.of(AiAssistantKey.RESEARCH_ASSISTANT, AiAssistantDomain.RESEARCH,
                        AiAssistantSystemRole.STUDENT, "STUDENT", AiCapability.LAB_POLICY_READ,
                        AiResourceType.LABORATORY, 10L, AiRequestedAction.READ),
                Arguments.of(AiAssistantKey.ADMIN_ASSISTANT, AiAssistantDomain.ADMIN,
                        AiAssistantSystemRole.ADMIN, "ADMIN", AiCapability.LAB_POLICY_READ,
                        AiResourceType.LABORATORY, 10L, AiRequestedAction.READ));
    }

    @ParameterizedTest(name = "{0} cannot request {4}")
    @MethodSource("crossDomainRequests")
    void deniesCrossDomainTamperingBeforeAnyDomainAdapterRead(
            AiAssistantKey key, AiAssistantDomain domain, AiAssistantSystemRole systemRole, String role,
            AiCapability capability, AiResourceType resourceType, Long resourceId, AiRequestedAction action) {
        User actor = activeUser(7L, "actor", role);
        authenticate(actor);
        stubAvailable(key, domain, systemRole);

        AiCapabilityDecision decision = resolver.resolve(request(
                key, 7L, capability, resourceType, resourceId, action));

        assertDenied(decision, DOMAIN_MISMATCH);
        verifyNoInteractions(adminAdapter, labAdapter, researchAdapter);
    }

    @Test
    void deniesMutationBeforeResourceAdapter() {
        AiCapabilityDecision decision = resolver.resolve(request(
                AiAssistantKey.LAB_ASSISTANT, 7L, AiCapability.LAB_BOOKING_DRAFT,
                AiResourceType.TIME_SLOT, 15L, AiRequestedAction.MUTATION));

        assertDenied(decision, PROHIBITED_ACTION);
        assertEquals(AiActionRiskBoundary.PROHIBITED, decision.riskBoundary());
        verifyNoInteractions(userRepository, registry);
        verifyNoInteractions(adminAdapter, labAdapter, researchAdapter);
    }

    static Stream<Arguments> registryFailures() {
        return Stream.of(
                Arguments.of(AiAssistantRegistryFailure.UNKNOWN_ASSISTANT, UNKNOWN_ASSISTANT),
                Arguments.of(AiAssistantRegistryFailure.ASSISTANT_UNAVAILABLE, ASSISTANT_DISABLED),
                Arguments.of(AiAssistantRegistryFailure.ROLE_INELIGIBLE, ROLE_NOT_ALLOWED),
                Arguments.of(AiAssistantRegistryFailure.MALFORMED_CATALOG, ASSISTANT_MISCONFIGURED));
    }

    @ParameterizedTest(name = "registry failure {0} is sanitized as {1}")
    @MethodSource("registryFailures")
    void registryFailureMatrixIsTypedRedactedAndStopsBeforeAdapters(
            AiAssistantRegistryFailure failure,
            com.web.labportalbackend.ai.enums.AiCapabilityDenialReason reason) {
        User student = activeUser(7L, "student", "STUDENT");
        authenticate(student);
        when(registry.getProfile(AiAssistantKey.LAB_ASSISTANT))
                .thenThrow(new AiAssistantRegistryException(failure));

        AiCapabilityDecision decision = resolver.resolve(request(
                AiAssistantKey.LAB_ASSISTANT, 7L, AiCapability.LAB_POLICY_READ,
                AiResourceType.LABORATORY, 10L, AiRequestedAction.READ));

        assertDenied(decision, reason);
        verifyNoInteractions(adminAdapter, labAdapter, researchAdapter);
    }

    @Test
    void ineligibleSystemRoleStopsBeforeAvailabilityAndResourceAdapters() {
        User student = activeUser(7L, "student", "STUDENT");
        authenticate(student);
        AiAssistantProfile managerOnly = profile(AiAssistantKey.LAB_ASSISTANT, AiAssistantDomain.LAB,
                AiAssistantSystemRole.LAB_MANAGER);
        when(registry.getProfile(AiAssistantKey.LAB_ASSISTANT)).thenReturn(managerOnly);

        AiCapabilityDecision decision = resolver.resolve(request(
                AiAssistantKey.LAB_ASSISTANT, 7L, AiCapability.LAB_POLICY_READ,
                AiResourceType.LABORATORY, 10L, AiRequestedAction.READ));

        assertDenied(decision, ROLE_NOT_ALLOWED);
        verify(registry, never()).getAvailableProfile(AiAssistantKey.LAB_ASSISTANT,
                AiAssistantSystemRole.STUDENT);
        verifyNoInteractions(adminAdapter, labAdapter, researchAdapter);
    }

    @Test
    void adapterExceptionAndMalformedResultFailClosedWithoutLeakingDetails() {
        User student = activeUser(7L, "student", "STUDENT");
        authenticate(student);
        stubAvailable(AiAssistantKey.LAB_ASSISTANT, AiAssistantDomain.LAB, AiAssistantSystemRole.STUDENT);
        AiCapabilityRequest request = request(AiAssistantKey.LAB_ASSISTANT, 7L,
                AiCapability.LAB_POLICY_READ, AiResourceType.LABORATORY, 10L, AiRequestedAction.READ);
        when(labAdapter.evaluate(student, request))
                .thenThrow(new IllegalStateException("private database detail"))
                .thenReturn(null);

        assertDenied(resolver.resolve(request), RESOURCE_UNAVAILABLE);
        assertDenied(resolver.resolve(request), RESOURCE_UNAVAILABLE);
        verifyNoInteractions(adminAdapter, researchAdapter);
    }

    @Test
    void composesOnlyFixedEvidenceAndNoAdditionalGrantForAllowedDecision() {
        User student = activeUser(7L, "student", "STUDENT");
        authenticate(student);
        stubAvailable(AiAssistantKey.LAB_ASSISTANT, AiAssistantDomain.LAB, AiAssistantSystemRole.STUDENT);
        AiCapabilityRequest request = request(AiAssistantKey.LAB_ASSISTANT, 7L,
                AiCapability.LAB_POLICY_READ, AiResourceType.LABORATORY, 10L, AiRequestedAction.READ);
        when(labAdapter.evaluate(student, request)).thenReturn(AiCapabilityPermissionAdapter.Evaluation.allowed(
                new AiCapabilityDecision.ResolvedResource(AiResourceType.LABORATORY, 10L,
                        10L, null, null, null, AiResourceScope.EXISTING_BUSINESS_PERMISSION),
                Set.of(AiCapabilityEvidence.DERIVED_RESOURCE, AiCapabilityEvidence.EXISTING_PERMISSION)));

        AiCapabilityDecision decision = resolver.requireAllowed(request);

        assertTrue(decision.allowed());
        assertTrue(decision.evidence().contains(AiCapabilityEvidence.NO_ADDITIONAL_GRANT));
        assertTrue(decision.evidence().contains(AiCapabilityEvidence.REGISTRY_PROFILE));
        assertThrows(UnsupportedOperationException.class,
                () -> decision.evidence().add(AiCapabilityEvidence.MANAGED_LAB));
    }

    @Test
    void requireAllowedThrowsOnlyTypedFixedDenialMessage() {
        AiCapabilityDeniedException exception = assertThrows(AiCapabilityDeniedException.class,
                () -> resolver.requireAllowed(null));

        assertEquals("MALFORMED_REQUEST", exception.getMessage());
        assertEquals(exception.decision(), exception.getDecision());
        assertDenied(exception.decision(), MALFORMED_REQUEST);
        verifyNoInteractions(userRepository, registry, adminAdapter, labAdapter, researchAdapter);
    }

    @Test
    void unusableActorAndRegistryFailureFailClosed() {
        User inactive = activeUser(7L, "inactive", "STUDENT");
        inactive.setActive(false);
        authenticate(inactive);
        assertDenied(resolver.resolve(request(AiAssistantKey.LAB_ASSISTANT, 7L,
                AiCapability.LAB_POLICY_READ, AiResourceType.LABORATORY, 10L,
                AiRequestedAction.READ)), ACTOR_UNAVAILABLE);

        authenticate(activeUser(8L, "active", "STUDENT"));
        when(registry.getProfile(AiAssistantKey.LAB_ASSISTANT))
                .thenThrow(new IllegalStateException("registry detail"));
        assertDenied(resolver.resolve(request(AiAssistantKey.LAB_ASSISTANT, 8L,
                AiCapability.LAB_POLICY_READ, AiResourceType.LABORATORY, 10L,
                AiRequestedAction.READ)), ASSISTANT_MISCONFIGURED);
        verifyNoInteractions(adminAdapter, labAdapter, researchAdapter);
    }

    private void stubAvailable(AiAssistantKey key, AiAssistantDomain domain, AiAssistantSystemRole role) {
        AiAssistantProfile profile = profile(key, domain, role);
        when(registry.getProfile(key)).thenReturn(profile);
        when(registry.getAvailableProfile(key, role)).thenReturn(profile);
    }

    private static AiAssistantProfile profile(AiAssistantKey key, AiAssistantDomain domain,
                                              AiAssistantSystemRole role) {
        return new AiAssistantProfile(key, domain, true, Set.of(role),
                "profile", "prompt-v1", null, domain.name().toLowerCase(),
                AiQuotaPolicyReference.AI_CONFIG_QUOTA,
                Set.of(domain == AiAssistantDomain.LAB ? AiAssistantToolGroup.LAB_READ : AiAssistantToolGroup.RESEARCH_READ),
                "suite-v1");
    }

    private void authenticate(User user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getUsername(), null, List.of()));
        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));
    }

    private static User activeUser(Long id, String username, String... roles) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setStatus(UserStatus.ACTIVE);
        user.setActive(true);
        user.setDeleted(false);
        for (String role : roles) {
            user.addRole(new Role(role, role));
        }
        return user;
    }

    private static AiCapabilityRequest request(AiAssistantKey key, Long actorId, AiCapability capability,
                                               AiResourceType type, Long resourceId, AiRequestedAction action) {
        return raw(key, actorId, capability, resource(type, resourceId), null, action);
    }

    private static AiCapabilityRequest raw(AiAssistantKey key, Long actorId, AiCapability capability,
                                           AiCapabilityRequest.ResourceReference resource,
                                           AiCapabilityRequest.ResourceReference parent,
                                           AiRequestedAction action) {
        return new AiCapabilityRequest(key, actorId, capability, resource, parent, action);
    }

    private static AiCapabilityRequest.ResourceReference resource(AiResourceType type, Long id) {
        return new AiCapabilityRequest.ResourceReference(type, id);
    }

    private static void assertDenied(AiCapabilityDecision decision,
                                     com.web.labportalbackend.ai.enums.AiCapabilityDenialReason reason) {
        assertFalse(decision.allowed());
        assertEquals(reason, decision.denialReason());
        assertNull(decision.resolvedResource());
        assertTrue(decision.evidence().isEmpty());
    }
}
