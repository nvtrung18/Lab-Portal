package com.web.labportalbackend.ai.context.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.web.labportalbackend.ai.context.AiContextReadDeniedException;
import com.web.labportalbackend.ai.context.AiDomainContextBuilder;
import com.web.labportalbackend.ai.context.AiLabContext;
import com.web.labportalbackend.ai.context.AiResearchAssistantContext;
import com.web.labportalbackend.ai.enums.AiActionRiskBoundary;
import com.web.labportalbackend.ai.enums.AiAssistantDomain;
import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.ai.enums.AiAssistantSystemRole;
import com.web.labportalbackend.ai.enums.AiCapability;
import com.web.labportalbackend.ai.enums.AiCapabilityDecisionReason;
import com.web.labportalbackend.ai.enums.AiRequestedAction;
import com.web.labportalbackend.ai.enums.AiResourceScope;
import com.web.labportalbackend.ai.enums.AiResourceType;
import com.web.labportalbackend.ai.service.AiCapabilityDecision;
import com.web.labportalbackend.ai.service.AiCapabilityRequest;
import com.web.labportalbackend.ai.service.AiCapabilityResolver;
import com.web.labportalbackend.ai.service.AiAuthorizedToolPolicy;
import com.web.labportalbackend.ai.service.AiToolPolicyDeniedException;
import com.web.labportalbackend.ai.service.AiToolPolicyDenialReason;
import com.web.labportalbackend.ai.service.AiToolPolicyResolver;
import com.web.labportalbackend.ai.service.AiResearchContext;
import com.web.labportalbackend.ai.service.impl.AiToolRegistryServiceImpl;
import com.web.labportalbackend.research.enums.ProjectStatus;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class AiContextFreshReadExecutorTest {

    @Test
    void changedFreshDecisionFailsBeforeAnyBuilderRead() {
        AiCapabilityResolver resolver = mock(AiCapabilityResolver.class);
        AiDomainContextBuilder admin = builder(AiAssistantDomain.ADMIN);
        AiDomainContextBuilder lab = builder(AiAssistantDomain.LAB);
        AiDomainContextBuilder research = builder(AiAssistantDomain.RESEARCH);
        AiCapabilityRequest request = request();
        AiCapabilityDecision preflight = allowed(7L);
        when(resolver.requireAllowed(request)).thenReturn(allowed(8L));
        AiContextFreshReadExecutor executor = new AiContextFreshReadExecutor(resolver, mock(AiToolPolicyResolver.class),
                List.of(admin, lab, research));

        assertThrows(AiContextReadDeniedException.class,
                () -> executor.execute(preflight, request, "request-1"));

        verify(lab, never()).build(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void selectedRoleChangedAfterPreflightFailsBeforeAnyBuilderRead() {
        AiCapabilityResolver resolver = mock(AiCapabilityResolver.class);
        AiDomainContextBuilder admin = builder(AiAssistantDomain.ADMIN);
        AiDomainContextBuilder lab = builder(AiAssistantDomain.LAB);
        AiDomainContextBuilder research = builder(AiAssistantDomain.RESEARCH);
        AiCapabilityRequest request = request();
        AiCapabilityDecision preliminary = allowed(7L, AiAssistantSystemRole.STUDENT);
        when(resolver.requireAllowed(request)).thenReturn(allowed(7L, AiAssistantSystemRole.LAB_MANAGER));
        AiContextFreshReadExecutor executor = new AiContextFreshReadExecutor(resolver, mock(AiToolPolicyResolver.class),
                List.of(admin, lab, research));

        assertThrows(AiContextReadDeniedException.class,
                () -> executor.execute(preliminary, request, "request-1"));

        verify(lab, never()).build(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void currentDecisionIsTheOnlyBuilderInput() {
        AiCapabilityResolver resolver = mock(AiCapabilityResolver.class);
        AiDomainContextBuilder admin = builder(AiAssistantDomain.ADMIN);
        AiDomainContextBuilder lab = builder(AiAssistantDomain.LAB);
        AiDomainContextBuilder research = builder(AiAssistantDomain.RESEARCH);
        AiCapabilityRequest request = request();
        AiCapabilityDecision current = allowed(7L);
        when(resolver.requireAllowed(request)).thenReturn(current);
        when(lab.build(org.mockito.ArgumentMatchers.any())).thenReturn(context());
        AiContextFreshReadExecutor executor = new AiContextFreshReadExecutor(resolver, policyResolver(current),
                List.of(admin, lab, research));

        var result = executor.execute(current, request, "request-1");

        assertEquals(10L, result.resource().labId());
        assertEquals(AiCapability.LAB_POLICY_READ, result.toolPolicy().descriptor().capability());
        verify(lab).build(org.mockito.ArgumentMatchers.argThat(input -> input.decision() == current));
    }

    @Test
    void freshResearchGroupDecisionCarriesSelectedRoleAndResolvedProjectToBuilder() {
        AiCapabilityResolver resolver = mock(AiCapabilityResolver.class);
        AiDomainContextBuilder admin = builder(AiAssistantDomain.ADMIN);
        AiDomainContextBuilder lab = builder(AiAssistantDomain.LAB);
        AiDomainContextBuilder research = builder(AiAssistantDomain.RESEARCH);
        AiCapabilityRequest request = researchGroupRequest();
        AiCapabilityDecision current = allowedResearchGroup();
        when(resolver.requireAllowed(request)).thenReturn(current);
        when(research.build(org.mockito.ArgumentMatchers.any())).thenReturn(researchContext());
        AiContextFreshReadExecutor executor = new AiContextFreshReadExecutor(resolver, policyResolver(current),
                List.of(admin, lab, research));

        var result = executor.execute(current, request, "request-1");

        assertEquals(20L, result.resource().projectId());
        verify(research).build(org.mockito.ArgumentMatchers.argThat(input ->
                input.decision() == current
                        && input.decision().selectedSystemRole() == AiAssistantSystemRole.STUDENT
                        && Long.valueOf(20L).equals(input.decision().resolvedResource().projectId())));
    }

    @Test
    void executorDeclaresAnIndependentReadCommittedReadOnlyTransaction() throws Exception {
        Method execute = AiContextFreshReadExecutor.class.getMethod("execute", AiCapabilityDecision.class,
                AiCapabilityRequest.class, String.class);
        Transactional transaction = execute.getAnnotation(Transactional.class);

        assertEquals(Propagation.REQUIRES_NEW, transaction.propagation());
        assertEquals(Isolation.READ_COMMITTED, transaction.isolation());
        assertEquals(true, transaction.readOnly());
    }

    private static AiDomainContextBuilder builder(AiAssistantDomain domain) {
        AiDomainContextBuilder builder = mock(AiDomainContextBuilder.class);
        when(builder.domain()).thenReturn(domain);
        return builder;
    }

    @Test
    void malformedCurrentProjectIdentityFailsBeforeAnyBuilderRead() {
        AiCapabilityResolver resolver = mock(AiCapabilityResolver.class);
        AiDomainContextBuilder admin = builder(AiAssistantDomain.ADMIN);
        AiDomainContextBuilder lab = builder(AiAssistantDomain.LAB);
        AiDomainContextBuilder research = builder(AiAssistantDomain.RESEARCH);
        AiCapabilityRequest request = researchProjectRequest();
        AiCapabilityDecision malformed = malformedResearchProject();
        when(resolver.requireAllowed(request)).thenReturn(malformed);
        AiContextFreshReadExecutor executor = new AiContextFreshReadExecutor(resolver, mock(AiToolPolicyResolver.class),
                List.of(admin, lab, research));

        assertThrows(AiContextReadDeniedException.class,
                () -> executor.execute(malformed, request, "request-1"));

        verify(research, never()).build(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void policyDenialFailsBeforeAnyBuilderRead() {
        AiCapabilityResolver resolver = mock(AiCapabilityResolver.class);
        AiToolPolicyResolver policyResolver = mock(AiToolPolicyResolver.class);
        AiDomainContextBuilder admin = builder(AiAssistantDomain.ADMIN);
        AiDomainContextBuilder lab = builder(AiAssistantDomain.LAB);
        AiDomainContextBuilder research = builder(AiAssistantDomain.RESEARCH);
        AiCapabilityRequest request = request();
        AiCapabilityDecision current = allowed(7L);
        when(resolver.requireAllowed(request)).thenReturn(current);
        when(policyResolver.resolve(current)).thenThrow(new AiToolPolicyDeniedException(AiToolPolicyDenialReason.RESOURCE_MISMATCH));
        AiContextFreshReadExecutor executor = new AiContextFreshReadExecutor(resolver, policyResolver,
                List.of(admin, lab, research));

        assertThrows(AiContextReadDeniedException.class,
                () -> executor.execute(current, request, "request-1"));

        verify(lab, never()).build(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void persistedRiskMetadataCannotOverrideTheFreshCapabilityRiskBoundary() {
        AiCapabilityResolver resolver = mock(AiCapabilityResolver.class);
        AiDomainContextBuilder admin = builder(AiAssistantDomain.ADMIN);
        AiDomainContextBuilder lab = builder(AiAssistantDomain.LAB);
        AiDomainContextBuilder research = builder(AiAssistantDomain.RESEARCH);
        AiCapabilityRequest request = request();
        AiCapabilityDecision storedMetadataDecision = new AiCapabilityDecision(true, 7L,
                AiAssistantSystemRole.STUDENT, AiAssistantKey.LAB_ASSISTANT, AiAssistantDomain.LAB,
                AiCapability.LAB_POLICY_READ,
                new AiCapabilityDecision.ResolvedResource(AiResourceType.LABORATORY, 10L, 10L,
                        null, null, null, AiResourceScope.EXISTING_BUSINESS_PERMISSION),
                AiCapabilityDecisionReason.ALLOWED_BY_EFFECTIVE_PERMISSION, null,
                AiActionRiskBoundary.CONFIRM_REQUIRED, Set.of(), null);
        AiContextFreshReadExecutor executor = new AiContextFreshReadExecutor(resolver, mock(AiToolPolicyResolver.class),
                List.of(admin, lab, research));

        assertThrows(AiContextReadDeniedException.class,
                () -> executor.execute(storedMetadataDecision, request, "request-1"));

        verify(resolver, never()).requireAllowed(request);
        verify(lab, never()).build(org.mockito.ArgumentMatchers.any());
    }

    private static AiToolPolicyResolver policyResolver(AiCapabilityDecision decision) {
        AiToolPolicyResolver resolver = mock(AiToolPolicyResolver.class);
        when(resolver.resolve(decision)).thenReturn(
                new AiAuthorizedToolPolicy(new AiToolRegistryServiceImpl().get(decision.capability())));
        return resolver;
    }

    private static AiCapabilityRequest request() {
        return new AiCapabilityRequest(AiAssistantKey.LAB_ASSISTANT, 7L, AiCapability.LAB_POLICY_READ,
                new AiCapabilityRequest.ResourceReference(AiResourceType.LABORATORY, 10L), null,
                AiRequestedAction.READ);
    }

    private static AiCapabilityRequest researchGroupRequest() {
        return new AiCapabilityRequest(AiAssistantKey.RESEARCH_ASSISTANT, 7L,
                AiCapability.RESEARCH_GROUP_SUMMARY,
                new AiCapabilityRequest.ResourceReference(AiResourceType.GROUP, 30L), null,
                AiRequestedAction.READ);
    }

    private static AiCapabilityRequest researchProjectRequest() {
        return new AiCapabilityRequest(AiAssistantKey.RESEARCH_ASSISTANT, 7L,
                AiCapability.RESEARCH_PROJECT_SUMMARY,
                new AiCapabilityRequest.ResourceReference(AiResourceType.PROJECT, 20L), null,
                AiRequestedAction.READ);
    }

    private static AiCapabilityDecision allowed(Long actorId) {
        return allowed(actorId, AiAssistantSystemRole.STUDENT);
    }

    private static AiCapabilityDecision allowed(Long actorId, AiAssistantSystemRole selectedRole) {
        return new AiCapabilityDecision(true, actorId, selectedRole, AiAssistantKey.LAB_ASSISTANT, AiAssistantDomain.LAB,
                AiCapability.LAB_POLICY_READ,
                new AiCapabilityDecision.ResolvedResource(AiResourceType.LABORATORY, 10L, 10L,
                        null, null, null, AiResourceScope.EXISTING_BUSINESS_PERMISSION),
                AiCapabilityDecisionReason.ALLOWED_BY_EFFECTIVE_PERMISSION, null,
                AiActionRiskBoundary.READ_ONLY, Set.of(), null);
    }

    private static AiCapabilityDecision allowedResearchGroup() {
        return new AiCapabilityDecision(true, 7L, AiAssistantSystemRole.STUDENT,
                AiAssistantKey.RESEARCH_ASSISTANT, AiAssistantDomain.RESEARCH,
                AiCapability.RESEARCH_GROUP_SUMMARY,
                new AiCapabilityDecision.ResolvedResource(AiResourceType.GROUP, 30L, 10L,
                        20L, 30L, null, AiResourceScope.GROUP_MEMBER),
                AiCapabilityDecisionReason.ALLOWED_BY_EFFECTIVE_PERMISSION, null,
                AiActionRiskBoundary.READ_ONLY, Set.of(), null);
    }

    private static AiCapabilityDecision malformedResearchProject() {
        return new AiCapabilityDecision(true, 7L, AiAssistantSystemRole.STUDENT,
                AiAssistantKey.RESEARCH_ASSISTANT, AiAssistantDomain.RESEARCH,
                AiCapability.RESEARCH_PROJECT_SUMMARY,
                new AiCapabilityDecision.ResolvedResource(AiResourceType.PROJECT, 10L, 1L,
                        20L, null, null, AiResourceScope.EXISTING_BUSINESS_PERMISSION),
                AiCapabilityDecisionReason.ALLOWED_BY_EFFECTIVE_PERMISSION, null,
                AiActionRiskBoundary.READ_ONLY, Set.of(), null);
    }

    private static AiLabContext context() {
        return new AiLabContext(new AiLabContext.Laboratory(10L, "L", null), null, null, null, null, null,
                false, "POLICY_INFORMATION_ONLY");
    }

    private static AiResearchAssistantContext researchContext() {
        AiResearchContext context = new AiResearchContext(
                new AiResearchContext.Identity(7L, List.of()),
                new AiResearchContext.Laboratory(10L, "Lab"),
                new AiResearchContext.Project(20L, "P", "Project", ProjectStatus.ONGOING, null, null),
                List.of(), List.of(), List.of());
        return new AiResearchAssistantContext(context,
                com.web.labportalbackend.ai.context.AiBoundedList.fromOverfetch(List.of(), 20),
                com.web.labportalbackend.ai.context.AiBoundedList.fromOverfetch(List.of(), 20),
                com.web.labportalbackend.ai.context.AiBoundedList.fromOverfetch(List.of(), 25),
                null, 30L, false);
    }
}
