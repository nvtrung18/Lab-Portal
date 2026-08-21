package com.web.labportalbackend.ai.service.impl;

import com.web.labportalbackend.ai.enums.AiActionRiskBoundary;
import com.web.labportalbackend.ai.service.AiToolActionGateDecision;
import com.web.labportalbackend.ai.service.AiToolActionGateService;
import com.web.labportalbackend.ai.service.AiToolDefinition;
import com.web.labportalbackend.ai.service.AiToolRegistry;
import org.springframework.stereotype.Service;

@Service
public final class AiToolActionGateServiceImpl implements AiToolActionGateService {

    private final AiToolRegistry toolRegistry;

    public AiToolActionGateServiceImpl(AiToolRegistry toolRegistry) {
        if (toolRegistry == null) {
            throw new IllegalArgumentException("tool registry is required");
        }
        this.toolRegistry = toolRegistry;
    }

    @Override
    public AiToolActionGateDecision classify(AiToolDefinition toolDefinition) {
        AiToolDefinition canonical = canonical(toolDefinition);
        AiActionRiskBoundary riskBoundary = canonical == null ? null : canonical.riskBoundary();
        if (riskBoundary == null) {
            return AiToolActionGateDecision.DENY;
        }
        return switch (riskBoundary) {
            case READ_ONLY -> AiToolActionGateDecision.ALLOW_READ_ONLY;
            case DRAFT_ONLY -> AiToolActionGateDecision.RETURN_DRAFT_ONLY;
            case CONFIRM_REQUIRED -> AiToolActionGateDecision.REQUIRE_CONFIRMATION;
            case APPROVAL_REQUIRED -> AiToolActionGateDecision.REQUIRE_APPROVAL;
            case PROHIBITED -> AiToolActionGateDecision.DENY;
        };
    }

    private AiToolDefinition canonical(AiToolDefinition candidate) {
        if (candidate == null || candidate.id() == null) {
            return null;
        }
        try {
            AiToolDefinition canonical = toolRegistry.get(candidate.id());
            return candidate.equals(canonical) ? canonical : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
