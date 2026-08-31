package com.web.labportalbackend.ai.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.web.labportalbackend.admin.audit.enums.AuditAction;
import com.web.labportalbackend.admin.audit.enums.AuditModule;
import com.web.labportalbackend.admin.audit.service.AuditLogService;
import com.web.labportalbackend.ai.entity.AiUsageLogEntity;
import com.web.labportalbackend.ai.enums.AiResourceType;
import com.web.labportalbackend.ai.repository.AiUsageLogRepository;
import com.web.labportalbackend.ai.service.AiAssistantAuditEvent;
import com.web.labportalbackend.ai.service.AiAuditUsageService;
import com.web.labportalbackend.ai.service.AiToolAuditEvent;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.enums.UserStatus;
import com.web.labportalbackend.notification.enums.NotificationEventType;
import com.web.labportalbackend.notification.enums.NotificationTargetModule;
import com.web.labportalbackend.notification.service.NotificationEmitter;
import java.util.Locale;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiAuditUsageServiceImpl implements AiAuditUsageService {

    private static final String ASSISTANT_DESCRIPTION = "AI assistant request outcome";
    private static final String TOOL_DESCRIPTION = "AI tool execution outcome";

    private final AuditLogService auditLogService;
    private final AiUsageLogRepository usageLogRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final NotificationEmitter notificationEmitter;

    public AiAuditUsageServiceImpl(AuditLogService auditLogService,
                                   AiUsageLogRepository usageLogRepository,
                                   UserRepository userRepository,
                                   ObjectMapper objectMapper,
                                   NotificationEmitter notificationEmitter) {
        this.auditLogService = auditLogService;
        this.usageLogRepository = usageLogRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.notificationEmitter = notificationEmitter;
    }

    @Override
    @Transactional
    public void recordAssistantRequest(AiAssistantAuditEvent event) {
        Objects.requireNonNull(event, "assistant audit event is required");
        User actor = resolveActor(event.actorId());
        auditLogService.log(actor, AuditAction.AI_ASSISTANT_REQUEST, AuditModule.AI,
                targetType(event.resourceType(), "AI_ASSISTANT"), event.resourceId(),
                ASSISTANT_DESCRIPTION, metadata(event));
        if (event.consumesUsage()) {
            usageLogRepository.save(AiUsageLogEntity.builder()
                    .userId(actor.getId())
                    .assistantKey(event.assistant())
                    .role(event.role().name())
                    .module(event.assistant().domain().name().toLowerCase(Locale.ROOT))
                    .labId(event.labId())
                    .projectId(event.projectId())
                    .groupId(event.groupId())
                    .promptTokens(event.promptTokens())
                    .completionTokens(event.completionTokens())
                    .status("SUCCESS")
                    .errorMessage(null)
                    .build());
        }
    }

    @Override
    @Transactional
    public void recordToolOutcome(AiToolAuditEvent event) {
        Objects.requireNonNull(event, "tool audit event is required");
        User actor = resolveActor(event.actorId());
        auditLogService.log(actor, AuditAction.AI_TOOL_OUTCOME, AuditModule.AI,
                targetType(event.resourceType(), "AI_TOOL"), event.resourceId(),
                TOOL_DESCRIPTION, metadata(event));
        notificationEmitter.emit(actor.getId(), NotificationEventType.AI_ACTION_STATUS_CHANGED,
                "AI action completed", "AI tool outcome: " + event.executionResult().name(),
                NotificationTargetModule.AI, event.resourceId(), event.assistant());
    }

    private String metadata(AiAssistantAuditEvent event) {
        return metadata(event.assistant() == null ? null : event.assistant().name(), null, null,
                event.action() == null ? null : event.action().name(),
                event.resourceType() == null ? null : event.resourceType().name(), event.resourceId(),
                event.modelVersion(), event.adapterVersion(), event.promptVersion(), event.requestId(),
                event.gateStatus().name(), event.executionResult().name(),
                event.failureCode() == null ? null : event.failureCode().name());
    }

    private String metadata(AiToolAuditEvent event) {
        return metadata(event.assistant() == null ? null : event.assistant().name(),
                event.toolId() == null ? null : event.toolId().value(), event.requestedToolId(),
                event.action() == null ? null : event.action().name(),
                event.resourceType() == null ? null : event.resourceType().name(), event.resourceId(),
                event.modelVersion(), event.adapterVersion(), event.promptVersion(), event.requestId(),
                event.gateStatus().name(), event.executionResult().name(),
                event.failureCode() == null ? null : event.failureCode().name());
    }

    private String metadata(String assistant, String toolId, String requestedToolId, String action,
                            String resourceType, Long resourceId, String modelVersion,
                            String adapterVersion, String promptVersion, String requestId,
                            String confirmation, String executionResult, String failureCode) {
        ObjectNode metadata = objectMapper.createObjectNode();
        putNullable(metadata, "assistant", assistant);
        putNullable(metadata, "toolId", toolId);
        putNullable(metadata, "requestedToolId", requestedToolId);
        putNullable(metadata, "action", action);
        putNullable(metadata, "resourceType", resourceType);
        if (resourceId == null) {
            metadata.putNull("resourceId");
        } else {
            metadata.put("resourceId", resourceId);
        }
        putNullable(metadata, "modelVersion", modelVersion);
        putNullable(metadata, "adapterVersion", adapterVersion);
        putNullable(metadata, "promptVersion", promptVersion);
        putNullable(metadata, "requestId", requestId);
        metadata.put("confirmation", confirmation);
        metadata.put("executionResult", executionResult);
        putNullable(metadata, "failureCode", failureCode);
        return metadata.toString();
    }

    private User resolveActor(Long expectedActorId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || authentication.getName() == null || authentication.getName().isBlank()
                || "anonymousUser".equals(authentication.getName())) {
            throw new AccessDeniedException("Authentication is required for AI audit attribution");
        }
        User actor = userRepository.findByUsername(authentication.getName())
                .filter(user -> user.getId() != null)
                .filter(user -> Boolean.TRUE.equals(user.getActive()))
                .filter(user -> !Boolean.TRUE.equals(user.getDeleted()))
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .filter(user -> user.getRoles() != null)
                .orElseThrow(() -> new AccessDeniedException("Authenticated AI audit actor is unavailable"));
        if (expectedActorId != null && !expectedActorId.equals(actor.getId())) {
            throw new AccessDeniedException("AI audit actor attribution changed");
        }
        return actor;
    }

    private static String targetType(AiResourceType resourceType, String fallback) {
        return resourceType == null ? fallback : resourceType.name();
    }

    private static void putNullable(ObjectNode target, String field, String value) {
        if (value == null) {
            target.putNull(field);
        } else {
            target.put(field, value);
        }
    }
}
