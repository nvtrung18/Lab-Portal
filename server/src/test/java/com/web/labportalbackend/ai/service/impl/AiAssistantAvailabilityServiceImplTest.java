package com.web.labportalbackend.ai.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.web.labportalbackend.ai.enums.AiAssistantDomain;
import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.ai.enums.AiAssistantSystemRole;
import com.web.labportalbackend.ai.enums.AiAssistantToolGroup;
import com.web.labportalbackend.ai.enums.AiQuotaPolicyReference;
import com.web.labportalbackend.ai.service.AiAssistantAvailabilityException;
import com.web.labportalbackend.ai.service.AiAssistantAvailabilityFailure;
import com.web.labportalbackend.ai.service.AiAssistantAvailability;
import com.web.labportalbackend.ai.service.AiAssistantProfile;
import com.web.labportalbackend.ai.service.AiAssistantRegistry;
import com.web.labportalbackend.ai.service.AiAssistantRegistryException;
import com.web.labportalbackend.ai.service.AiAssistantRegistryFailure;
import com.web.labportalbackend.ai.service.AiConfigQuotaService;
import com.web.labportalbackend.ai.service.AiQuotaCheckRequest;
import com.web.labportalbackend.ai.service.AiQuotaDecision;
import com.web.labportalbackend.ai.service.AiQuotaDenialReason;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AiAssistantAvailabilityServiceImplTest {

    @Mock AiAssistantRegistry assistantRegistry;
    @Mock AiConfigQuotaService configQuotaService;
    @Mock UserRepository userRepository;

    private AiAssistantAvailabilityServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AiAssistantAvailabilityServiceImpl(assistantRegistry, configQuotaService, userRepository);
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void enabledAssistantAllowedRoleAndAvailableQuotaReturnCanonicalProfile() {
        User actor = authenticate(activeUser(7L, "student", "STUDENT"));
        AiAssistantProfile profile = profile(AiAssistantKey.LAB_ASSISTANT, true,
                Set.of(AiAssistantSystemRole.ADMIN, AiAssistantSystemRole.LAB_MANAGER,
                        AiAssistantSystemRole.STUDENT));
        when(assistantRegistry.getProfile(AiAssistantKey.LAB_ASSISTANT)).thenReturn(profile);
        when(assistantRegistry.getAvailableProfile(AiAssistantKey.LAB_ASSISTANT, AiAssistantSystemRole.STUDENT))
                .thenReturn(profile);
        when(configQuotaService.evaluate(any())).thenReturn(AiQuotaDecision.allow());

        AiAssistantProfile available = service.requireAvailable(AiAssistantKey.LAB_ASSISTANT);

        assertSame(profile, available);
        ArgumentCaptor<AiQuotaCheckRequest> quotaRequest = ArgumentCaptor.forClass(AiQuotaCheckRequest.class);
        verify(configQuotaService).evaluate(quotaRequest.capture());
        assertEquals(actor.getId(), quotaRequest.getValue().userId());
        assertEquals(AiAssistantKey.LAB_ASSISTANT, quotaRequest.getValue().assistantKey());
        assertEquals("STUDENT", quotaRequest.getValue().role());
        assertEquals("lab", quotaRequest.getValue().module());
        assertEquals(0, quotaRequest.getValue().contextTokens());

        InOrder gateOrder = inOrder(assistantRegistry, configQuotaService);
        gateOrder.verify(assistantRegistry).getProfile(AiAssistantKey.LAB_ASSISTANT);
        gateOrder.verify(assistantRegistry)
                .getAvailableProfile(AiAssistantKey.LAB_ASSISTANT, AiAssistantSystemRole.STUDENT);
        gateOrder.verify(configQuotaService).evaluate(any());
    }

    @Test
    void actorAwareAvailabilityReturnsOnlyServerResolvedIdentityAndRole() {
        User actor = authenticate(activeUser(7L, "student", "STUDENT"));
        AiAssistantProfile profile = profile(AiAssistantKey.LAB_ASSISTANT, true,
                Set.of(AiAssistantSystemRole.STUDENT));
        when(assistantRegistry.getProfile(AiAssistantKey.LAB_ASSISTANT)).thenReturn(profile);
        when(assistantRegistry.getAvailableProfile(AiAssistantKey.LAB_ASSISTANT, AiAssistantSystemRole.STUDENT))
                .thenReturn(profile);
        when(configQuotaService.evaluate(any())).thenReturn(AiQuotaDecision.allow());

        AiAssistantAvailability availability = service.requireAvailableForActor(AiAssistantKey.LAB_ASSISTANT);

        assertSame(profile, availability.profile());
        assertEquals(actor.getId(), availability.actorId());
        assertEquals(AiAssistantSystemRole.STUDENT, availability.selectedSystemRole());
    }

    @Test
    void catalogDisabledAssistantIsDeniedBeforeActorOrQuotaResolution() {
        AiAssistantProfile profile = profile(AiAssistantKey.RESEARCH_ASSISTANT, false,
                Set.of(AiAssistantSystemRole.STUDENT));
        when(assistantRegistry.getProfile(AiAssistantKey.RESEARCH_ASSISTANT)).thenReturn(profile);

        AiAssistantAvailabilityException exception = assertThrows(AiAssistantAvailabilityException.class,
                () -> service.requireAvailable(AiAssistantKey.RESEARCH_ASSISTANT));

        assertEquals(AiAssistantAvailabilityFailure.ASSISTANT_UNAVAILABLE, exception.failure());
        verify(assistantRegistry, never()).getAvailableProfile(any(), any());
        verifyNoInteractions(userRepository, configQuotaService);
    }

    @Test
    void persistedDisabledAssistantIsDeniedBeforeQuota() {
        authenticate(activeUser(7L, "student", "STUDENT"));
        AiAssistantProfile profile = profile(AiAssistantKey.RESEARCH_ASSISTANT, true,
                Set.of(AiAssistantSystemRole.STUDENT));
        when(assistantRegistry.getProfile(AiAssistantKey.RESEARCH_ASSISTANT)).thenReturn(profile);
        when(assistantRegistry.getAvailableProfile(AiAssistantKey.RESEARCH_ASSISTANT,
                AiAssistantSystemRole.STUDENT)).thenThrow(new AiAssistantRegistryException(
                        AiAssistantRegistryFailure.ASSISTANT_UNAVAILABLE));

        AiAssistantAvailabilityException exception = assertThrows(AiAssistantAvailabilityException.class,
                () -> service.requireAvailable(AiAssistantKey.RESEARCH_ASSISTANT));

        assertEquals(AiAssistantAvailabilityFailure.ASSISTANT_UNAVAILABLE, exception.failure());
        verifyNoInteractions(configQuotaService);
    }

    @Test
    void studentCannotGainAdminAssistantThroughAssistantKey() {
        authenticate(activeUser(7L, "student", "STUDENT"));
        AiAssistantProfile profile = profile(AiAssistantKey.ADMIN_ASSISTANT, true,
                Set.of(AiAssistantSystemRole.ADMIN));
        when(assistantRegistry.getProfile(AiAssistantKey.ADMIN_ASSISTANT)).thenReturn(profile);

        AiAssistantAvailabilityException exception = assertThrows(AiAssistantAvailabilityException.class,
                () -> service.requireAvailable(AiAssistantKey.ADMIN_ASSISTANT));

        assertEquals(AiAssistantAvailabilityFailure.ROLE_NOT_ALLOWED, exception.failure());
        verify(assistantRegistry, never()).getAvailableProfile(any(), any());
        verifyNoInteractions(configQuotaService);
    }

    static Stream<Arguments> quotaDenials() {
        return Stream.of(
                Arguments.of(AiQuotaDenialReason.CONTEXT_LIMIT_EXCEEDED,
                        AiAssistantAvailabilityFailure.QUOTA_EXCEEDED),
                Arguments.of(AiQuotaDenialReason.ASSISTANT_DAILY_LIMIT_REACHED,
                        AiAssistantAvailabilityFailure.QUOTA_EXCEEDED),
                Arguments.of(AiQuotaDenialReason.QUOTA_DAILY_LIMIT_REACHED,
                        AiAssistantAvailabilityFailure.QUOTA_EXCEEDED),
                Arguments.of(AiQuotaDenialReason.ASSISTANT_CONFIGURATION_UNAVAILABLE,
                        AiAssistantAvailabilityFailure.CONFIGURATION_UNAVAILABLE),
                Arguments.of(AiQuotaDenialReason.ASSISTANT_DISABLED,
                        AiAssistantAvailabilityFailure.ASSISTANT_UNAVAILABLE),
                Arguments.of(AiQuotaDenialReason.QUOTA_DISABLED,
                        AiAssistantAvailabilityFailure.CONFIGURATION_UNAVAILABLE));
    }

    @ParameterizedTest
    @MethodSource("quotaDenials")
    void quotaAndConfigurationDenialsFailClosed(AiQuotaDenialReason quotaReason,
                                                AiAssistantAvailabilityFailure expectedFailure) {
        authenticate(activeUser(7L, "manager", "LAB_MANAGER"));
        AiAssistantProfile profile = profile(AiAssistantKey.RESEARCH_ASSISTANT, true,
                Set.of(AiAssistantSystemRole.LAB_MANAGER));
        when(assistantRegistry.getProfile(AiAssistantKey.RESEARCH_ASSISTANT)).thenReturn(profile);
        when(assistantRegistry.getAvailableProfile(AiAssistantKey.RESEARCH_ASSISTANT,
                AiAssistantSystemRole.LAB_MANAGER)).thenReturn(profile);
        when(configQuotaService.evaluate(any())).thenReturn(AiQuotaDecision.deny(quotaReason));

        AiAssistantAvailabilityException exception = assertThrows(AiAssistantAvailabilityException.class,
                () -> service.requireAvailable(AiAssistantKey.RESEARCH_ASSISTANT));

        assertEquals(expectedFailure, exception.failure());
    }

    @Test
    void missingAuthenticationFailsClosedBeforeActorLookup() {
        AiAssistantProfile profile = profile(AiAssistantKey.LAB_ASSISTANT, true,
                Set.of(AiAssistantSystemRole.STUDENT));
        when(assistantRegistry.getProfile(AiAssistantKey.LAB_ASSISTANT)).thenReturn(profile);

        AiAssistantAvailabilityException exception = assertThrows(AiAssistantAvailabilityException.class,
                () -> service.requireAvailable(AiAssistantKey.LAB_ASSISTANT));

        assertEquals(AiAssistantAvailabilityFailure.UNAUTHENTICATED, exception.failure());
        verifyNoInteractions(userRepository, configQuotaService);
    }

    @Test
    void missingOrInactiveActorFailsClosed() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("missing", null, List.of()));
        when(userRepository.findByUsername("missing")).thenReturn(Optional.empty());
        AiAssistantProfile profile = profile(AiAssistantKey.LAB_ASSISTANT, true,
                Set.of(AiAssistantSystemRole.STUDENT));
        when(assistantRegistry.getProfile(AiAssistantKey.LAB_ASSISTANT)).thenReturn(profile);

        AiAssistantAvailabilityException exception = assertThrows(AiAssistantAvailabilityException.class,
                () -> service.requireAvailable(AiAssistantKey.LAB_ASSISTANT));

        assertEquals(AiAssistantAvailabilityFailure.ACTOR_UNAVAILABLE, exception.failure());
        verifyNoInteractions(configQuotaService);
    }

    @Test
    void missingActorRoleMetadataFailsClosed() {
        User actor = activeUser(7L, "student", "STUDENT");
        actor.setRoles(null);
        authenticate(actor);
        AiAssistantProfile profile = profile(AiAssistantKey.LAB_ASSISTANT, true,
                Set.of(AiAssistantSystemRole.STUDENT));
        when(assistantRegistry.getProfile(AiAssistantKey.LAB_ASSISTANT)).thenReturn(profile);

        AiAssistantAvailabilityException exception = assertThrows(AiAssistantAvailabilityException.class,
                () -> service.requireAvailable(AiAssistantKey.LAB_ASSISTANT));

        assertEquals(AiAssistantAvailabilityFailure.ACTOR_UNAVAILABLE, exception.failure());
        verifyNoInteractions(configQuotaService);
    }

    @Test
    void unknownActorRoleDoesNotDefaultToPrivilegedAccess() {
        authenticate(activeUser(7L, "external", "SUPERUSER"));
        AiAssistantProfile profile = profile(AiAssistantKey.LAB_ASSISTANT, true,
                Set.of(AiAssistantSystemRole.ADMIN, AiAssistantSystemRole.LAB_MANAGER,
                        AiAssistantSystemRole.STUDENT));
        when(assistantRegistry.getProfile(AiAssistantKey.LAB_ASSISTANT)).thenReturn(profile);

        AiAssistantAvailabilityException exception = assertThrows(AiAssistantAvailabilityException.class,
                () -> service.requireAvailable(AiAssistantKey.LAB_ASSISTANT));

        assertEquals(AiAssistantAvailabilityFailure.ROLE_NOT_ALLOWED, exception.failure());
        verify(assistantRegistry, never()).getAvailableProfile(any(), any());
        verifyNoInteractions(configQuotaService);
    }

    @Test
    void malformedProfileFailsClosedWithoutResolvingActorOrQuota() {
        AiAssistantProfile profile = org.mockito.Mockito.mock(AiAssistantProfile.class);
        when(profile.key()).thenReturn(AiAssistantKey.LAB_ASSISTANT);
        when(profile.domain()).thenReturn(AiAssistantDomain.RESEARCH);
        when(assistantRegistry.getProfile(AiAssistantKey.LAB_ASSISTANT)).thenReturn(profile);

        AiAssistantAvailabilityException exception = assertThrows(AiAssistantAvailabilityException.class,
                () -> service.requireAvailable(AiAssistantKey.LAB_ASSISTANT));

        assertEquals(AiAssistantAvailabilityFailure.CONFIGURATION_UNAVAILABLE, exception.failure());
        verifyNoInteractions(userRepository, configQuotaService);
    }

    @Test
    void quotaServiceFailureFailsClosed() {
        authenticate(activeUser(7L, "student", "STUDENT"));
        AiAssistantProfile profile = profile(AiAssistantKey.LAB_ASSISTANT, true,
                Set.of(AiAssistantSystemRole.STUDENT));
        when(assistantRegistry.getProfile(AiAssistantKey.LAB_ASSISTANT)).thenReturn(profile);
        when(assistantRegistry.getAvailableProfile(AiAssistantKey.LAB_ASSISTANT, AiAssistantSystemRole.STUDENT))
                .thenReturn(profile);
        when(configQuotaService.evaluate(any())).thenThrow(new IllegalStateException("database unavailable"));

        AiAssistantAvailabilityException exception = assertThrows(AiAssistantAvailabilityException.class,
                () -> service.requireAvailable(AiAssistantKey.LAB_ASSISTANT));

        assertEquals(AiAssistantAvailabilityFailure.CONFIGURATION_UNAVAILABLE, exception.failure());
    }

    private User authenticate(User actor) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actor.getUsername(), null, List.of()));
        when(userRepository.findByUsername(actor.getUsername())).thenReturn(Optional.of(actor));
        return actor;
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

    private static AiAssistantProfile profile(AiAssistantKey key, boolean catalogEnabled,
                                              Set<AiAssistantSystemRole> roles) {
        AiAssistantDomain domain = key.domain();
        return new AiAssistantProfile(key, domain, catalogEnabled, roles,
                domain.name().toLowerCase(), domain.name().toLowerCase() + "-v1", null,
                domain.name().toLowerCase(), AiQuotaPolicyReference.AI_CONFIG_QUOTA,
                Set.of(switch (domain) {
                    case ADMIN -> AiAssistantToolGroup.ADMIN_READ;
                    case LAB -> AiAssistantToolGroup.LAB_READ;
                    case RESEARCH -> AiAssistantToolGroup.RESEARCH_READ;
                }), domain.name().toLowerCase() + "-suite-v1");
    }
}
