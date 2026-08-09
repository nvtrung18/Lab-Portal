package com.web.labportalbackend.ai.context.impl;

import com.web.labportalbackend.admin.audit.repository.AiAuditSummaryBucket;
import com.web.labportalbackend.admin.audit.repository.AuditLogRepository;
import com.web.labportalbackend.ai.context.AiAdminContext;
import com.web.labportalbackend.ai.context.AiBoundedList;
import com.web.labportalbackend.ai.context.AiContextReadDeniedException;
import com.web.labportalbackend.ai.context.AiDomainContext;
import com.web.labportalbackend.ai.context.AiDomainContextBuilder;
import com.web.labportalbackend.ai.context.TrustedContextInput;
import com.web.labportalbackend.ai.enums.AiAssistantDomain;
import com.web.labportalbackend.ai.enums.AiCapability;
import com.web.labportalbackend.auth.repository.UserRepository;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
public class AiAdminAssistantContextBuilder implements AiDomainContextBuilder {

    private static final int AUDIT_LIMIT = 14;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;

    public AiAdminAssistantContextBuilder(UserRepository userRepository, AuditLogRepository auditLogRepository) {
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Override public AiAssistantDomain domain() { return AiAssistantDomain.ADMIN; }

    @Override
    public AiDomainContext build(TrustedContextInput input) {
        if (input.decision().domain() != domain()) {
            throw new AiContextReadDeniedException();
        }
        if (!userRepository.existsAiContextAdminActor(input.actorId())) {
            throw new AiContextReadDeniedException();
        }
        AiCapability capability = input.decision().capability();
        AiAdminContext.SystemSummary summary = userRepository.findAiContextSystemSummary(input.actorId());
        if (summary == null) {
            throw new AiContextReadDeniedException();
        }
        AiAdminContext.TargetUser target = null;
        if (capability == AiCapability.ADMIN_USER_STATUS_LOOKUP || capability == AiCapability.ADMIN_ACCOUNT_ACTION_DRAFT) {
            target = userRepository.findAiContextTarget(input.actorId(), input.decision().resolvedResource().id())
                    .orElseThrow(AiContextReadDeniedException::new);
        }
        List<AiAdminContext.AuditBucket> buckets = capability == AiCapability.ADMIN_AUDIT_SUMMARY
                ? auditLogRepository.findAiContextAuditBuckets(input.actorId(),
                        input.builtAt().minus(14, ChronoUnit.DAYS), PageRequest.of(0, AUDIT_LIMIT + 1)).stream()
                        .map(this::toBucket).toList()
                : List.of();
        return new AiAdminContext(summary, target,
                AiBoundedList.fromOverfetch(buckets, AUDIT_LIMIT),
                capability.action().name().equals("DRAFT"));
    }

    private AiAdminContext.AuditBucket toBucket(AiAuditSummaryBucket bucket) {
        return new AiAdminContext.AuditBucket(java.sql.Date.valueOf(bucket.getDay().toString()).toLocalDate(),
                bucket.getModule(), bucket.getAction(), bucket.getCount());
    }
}
