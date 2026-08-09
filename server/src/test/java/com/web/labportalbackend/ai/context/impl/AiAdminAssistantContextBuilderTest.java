package com.web.labportalbackend.ai.context.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.web.labportalbackend.admin.audit.repository.AuditLogRepository;
import com.web.labportalbackend.ai.context.AiAdminContext;
import com.web.labportalbackend.ai.context.TrustedContextInput;
import com.web.labportalbackend.ai.enums.*;
import com.web.labportalbackend.ai.service.AiCapabilityDecision;
import com.web.labportalbackend.auth.repository.UserRepository;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

class AiAdminAssistantContextBuilderTest {
    @Test void currentAcceptedActorIsBoundToAdminAggregateRead() {
        UserRepository users = mock(UserRepository.class); AuditLogRepository audits = mock(AuditLogRepository.class);
        when(users.existsAiContextAdminActor(7L)).thenReturn(true);
        when(users.findAiContextSystemSummary(7L)).thenReturn(new AiAdminContext.SystemSummary(2, 3));
        AiAdminAssistantContextBuilder builder = new AiAdminAssistantContextBuilder(users, audits);
        AiAdminContext context = (AiAdminContext) builder.build(input());
        assertEquals(2, context.systemSummary().activeUserCount());
        verify(users).findAiContextSystemSummary(7L);
    }
    @Test void auditBucketsOverfetchToReportTruncationAccurately() {
        UserRepository users = mock(UserRepository.class); AuditLogRepository audits = mock(AuditLogRepository.class);
        when(users.existsAiContextAdminActor(7L)).thenReturn(true);
        when(users.findAiContextSystemSummary(7L)).thenReturn(new AiAdminContext.SystemSummary(2, 3));
        when(audits.findAiContextAuditBuckets(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(java.util.List.of());
        AiAdminAssistantContextBuilder builder = new AiAdminAssistantContextBuilder(users, audits);
        AiAdminContext context = (AiAdminContext) builder.build(auditInput());
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(audits).findAiContextAuditBuckets(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.any(), page.capture());
        assertEquals(15, page.getValue().getPageSize());
        assertEquals(false, context.auditBuckets().truncated());
    }
    @Test void staleAdminActorFailsClosedBeforeAnyAggregateRead() {
        UserRepository users = mock(UserRepository.class); AuditLogRepository audits = mock(AuditLogRepository.class);
        when(users.existsAiContextAdminActor(7L)).thenReturn(false);
        AiAdminAssistantContextBuilder builder = new AiAdminAssistantContextBuilder(users, audits);

        assertThrows(com.web.labportalbackend.ai.context.AiContextReadDeniedException.class,
                () -> builder.build(auditInput()));

        verify(users).existsAiContextAdminActor(7L);
        verify(users, never()).findAiContextSystemSummary(7L);
        verify(audits, never()).findAiContextAuditBuckets(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
    private static TrustedContextInput input() {
        AiCapabilityDecision d = new AiCapabilityDecision(true, 7L, com.web.labportalbackend.ai.enums.AiAssistantSystemRole.ADMIN, AiAssistantKey.ADMIN_ASSISTANT,
                AiAssistantDomain.ADMIN, AiCapability.ADMIN_SYSTEM_SUMMARY,
                new AiCapabilityDecision.ResolvedResource(AiResourceType.SYSTEM, null, null, null, null, null, AiResourceScope.GLOBAL),
                AiCapabilityDecisionReason.ALLOWED_BY_EFFECTIVE_PERMISSION, null, AiActionRiskBoundary.READ_ONLY, Set.of(), null);
        return new TrustedContextInput(d, 7L, null, Instant.now());
    }
    private static TrustedContextInput auditInput() {
        AiCapabilityDecision d = new AiCapabilityDecision(true, 7L, com.web.labportalbackend.ai.enums.AiAssistantSystemRole.ADMIN, AiAssistantKey.ADMIN_ASSISTANT,
                AiAssistantDomain.ADMIN, AiCapability.ADMIN_AUDIT_SUMMARY,
                new AiCapabilityDecision.ResolvedResource(AiResourceType.SYSTEM, null, null, null, null, null, AiResourceScope.GLOBAL),
                AiCapabilityDecisionReason.ALLOWED_BY_EFFECTIVE_PERMISSION, null, AiActionRiskBoundary.READ_ONLY, Set.of(), null);
        return new TrustedContextInput(d, 7L, null, Instant.now());
    }
}
