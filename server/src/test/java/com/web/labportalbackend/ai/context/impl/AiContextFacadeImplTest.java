package com.web.labportalbackend.ai.context.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.web.labportalbackend.ai.context.AiAuthorizedContext;
import com.web.labportalbackend.ai.context.AiContextBuildRequest;
import com.web.labportalbackend.ai.context.AiContextReadDeniedException;
import com.web.labportalbackend.ai.context.AiDomainContextBuilder;
import com.web.labportalbackend.ai.context.AiLabContext;
import com.web.labportalbackend.ai.enums.AiActionRiskBoundary;
import com.web.labportalbackend.ai.enums.AiAssistantDomain;
import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.ai.enums.AiCapability;
import com.web.labportalbackend.ai.enums.AiCapabilityDecisionReason;
import com.web.labportalbackend.ai.enums.AiRequestedAction;
import com.web.labportalbackend.ai.enums.AiResourceScope;
import com.web.labportalbackend.ai.enums.AiResourceType;
import com.web.labportalbackend.ai.service.AiCapabilityDecision;
import com.web.labportalbackend.ai.service.AiCapabilityRequest;
import com.web.labportalbackend.ai.service.AiCapabilityResolver;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

class AiContextFacadeImplTest {

    @Test
    void facadeUsesPreflightOnlyAndDelegatesFreshReadToSeparateExecutor() {
        AiCapabilityResolver resolver = mock(AiCapabilityResolver.class);
        AiDomainContextBuilder admin = builder(AiAssistantDomain.ADMIN);
        AiDomainContextBuilder lab = builder(AiAssistantDomain.LAB);
        AiDomainContextBuilder research = builder(AiAssistantDomain.RESEARCH);
        AiCapabilityRequest request = request();
        when(resolver.requireAllowed(request)).thenReturn(allowed());
        when(lab.build(org.mockito.ArgumentMatchers.any())).thenReturn(context());
        AiContextFreshReadExecutor executor = new AiContextFreshReadExecutor(resolver,
                List.of(admin, lab, research));
        AiContextFacadeImpl facade = new AiContextFacadeImpl(resolver, executor);

        AiAuthorizedContext result = facade.build(new AiContextBuildRequest(request, "request-1"));

        assertEquals("request-1", result.requestId());
        verify(resolver, org.mockito.Mockito.times(2)).requireAllowed(request);
        verify(lab).build(org.mockito.ArgumentMatchers.argThat(input -> input.actorId().equals(7L)));
    }

    @Test
    void invalidPreflightFailsBeforeFreshExecutorOrBuilderDataAccess() {
        AiCapabilityResolver resolver = mock(AiCapabilityResolver.class);
        AiDomainContextBuilder admin = builder(AiAssistantDomain.ADMIN);
        AiDomainContextBuilder lab = builder(AiAssistantDomain.LAB);
        AiDomainContextBuilder research = builder(AiAssistantDomain.RESEARCH);
        AiCapabilityRequest request = request();
        AiCapabilityDecision invalid = new AiCapabilityDecision(false, null, null, AiAssistantKey.LAB_ASSISTANT,
                AiAssistantDomain.LAB, AiCapability.LAB_POLICY_READ, null,
                AiCapabilityDecisionReason.DENIED_BY_REQUEST,
                com.web.labportalbackend.ai.enums.AiCapabilityDenialReason.ACTOR_UNAVAILABLE,
                AiActionRiskBoundary.READ_ONLY, Set.of(), null);
        when(resolver.requireAllowed(request)).thenReturn(invalid);
        AiContextFacadeImpl facade = new AiContextFacadeImpl(resolver,
                new AiContextFreshReadExecutor(resolver, List.of(admin, lab, research)));

        assertThrows(AiContextReadDeniedException.class,
                () -> facade.build(new AiContextBuildRequest(request, "request-1")));

        verify(resolver).requireAllowed(request);
        verify(lab, never()).build(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void facadeDoesNotOwnTheTransactionBoundary() throws Exception {
        Method build = AiContextFacadeImpl.class.getMethod("build", AiContextBuildRequest.class);

        assertEquals(null, build.getAnnotation(Transactional.class));
    }

    private static AiDomainContextBuilder builder(AiAssistantDomain domain) {
        AiDomainContextBuilder builder = mock(AiDomainContextBuilder.class);
        when(builder.domain()).thenReturn(domain);
        return builder;
    }

    private static AiCapabilityRequest request() {
        return new AiCapabilityRequest(AiAssistantKey.LAB_ASSISTANT, 7L, AiCapability.LAB_POLICY_READ,
                new AiCapabilityRequest.ResourceReference(AiResourceType.LABORATORY, 10L), null,
                AiRequestedAction.READ);
    }

    private static AiCapabilityDecision allowed() {
        return new AiCapabilityDecision(true, 7L, com.web.labportalbackend.ai.enums.AiAssistantSystemRole.STUDENT, AiAssistantKey.LAB_ASSISTANT, AiAssistantDomain.LAB,
                AiCapability.LAB_POLICY_READ,
                new AiCapabilityDecision.ResolvedResource(AiResourceType.LABORATORY, 10L, 10L,
                        null, null, null, AiResourceScope.EXISTING_BUSINESS_PERMISSION),
                AiCapabilityDecisionReason.ALLOWED_BY_EFFECTIVE_PERMISSION, null,
                AiActionRiskBoundary.READ_ONLY, Set.of(), null);
    }

    private static AiLabContext context() {
        return new AiLabContext(new AiLabContext.Laboratory(10L, "L", null), null, null, null,
                false, "POLICY_INFORMATION_ONLY");
    }
}
