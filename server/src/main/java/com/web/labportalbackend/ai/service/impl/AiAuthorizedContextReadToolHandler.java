package com.web.labportalbackend.ai.service.impl;

import com.web.labportalbackend.ai.context.AiAuthorizedContext;
import com.web.labportalbackend.ai.context.AiDomainContext;
import com.web.labportalbackend.ai.enums.AiActionRiskBoundary;
import com.web.labportalbackend.ai.enums.AiToolId;
import com.web.labportalbackend.ai.service.AiToolDefinition;
import com.web.labportalbackend.ai.service.AiToolHandler;
import com.web.labportalbackend.ai.service.AiToolRegistry;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Executes the safe read subset by returning only the freshly rebuilt, bounded P9-T3 context. */
@Component
public final class AiAuthorizedContextReadToolHandler implements AiToolHandler {

    private final Set<AiToolId> supportedToolIds;

    public AiAuthorizedContextReadToolHandler(AiToolRegistry toolRegistry) {
        if (toolRegistry == null) {
            throw new IllegalArgumentException("toolRegistry is required");
        }
        this.supportedToolIds = Arrays.stream(AiToolId.values())
                .filter(toolId -> isReadOnly(toolRegistry.get(toolId)))
                .collect(Collectors.toUnmodifiableSet());
        if (supportedToolIds.isEmpty()) {
            throw new IllegalArgumentException("at least one canonical read tool is required");
        }
    }

    @Override
    public Set<AiToolId> supportedToolIds() {
        return supportedToolIds;
    }

    @Override
    public AiDomainContext execute(AiToolId toolId, AiAuthorizedContext authorizedContext) {
        if (toolId == null || !supportedToolIds.contains(toolId) || authorizedContext == null
                || authorizedContext.toolPolicy() == null
                || authorizedContext.toolPolicy().descriptor().id() != toolId
                || authorizedContext.toolPolicy().descriptor().riskBoundary() != AiActionRiskBoundary.READ_ONLY
                || authorizedContext.context() == null) {
            throw new IllegalArgumentException("authorized read tool context is invalid");
        }
        return authorizedContext.context();
    }

    private static boolean isReadOnly(AiToolDefinition definition) {
        return definition != null && definition.riskBoundary() == AiActionRiskBoundary.READ_ONLY;
    }
}
