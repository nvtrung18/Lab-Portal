package com.web.labportalbackend.ai.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.web.labportalbackend.ai.client.AiGatewayRequest;
import com.web.labportalbackend.ai.client.AiToolPlanningClient;
import com.web.labportalbackend.ai.client.AiToolPlanningDecision;
import com.web.labportalbackend.ai.client.AiToolPlanningResponse;
import com.web.labportalbackend.ai.dto.request.AiAssistantChatRequest;
import com.web.labportalbackend.ai.dto.request.AiUnifiedChatRequest;
import com.web.labportalbackend.ai.dto.response.AiAssistantChatResponse;
import com.web.labportalbackend.ai.dto.response.AiUnifiedChatResponse;
import com.web.labportalbackend.ai.dto.response.AiActionPreviewResponse;
import com.web.labportalbackend.ai.enums.AiCapability;
import com.web.labportalbackend.ai.enums.AiUnifiedChatResponseType;
import com.web.labportalbackend.ai.service.AiAssistantGatewayService;
import com.web.labportalbackend.ai.service.AiActionSuggestionService;
import com.web.labportalbackend.ai.service.AiToolCandidate;
import com.web.labportalbackend.ai.service.AiToolCandidateCatalog;
import com.web.labportalbackend.ai.service.AiToolDefinition;
import com.web.labportalbackend.ai.service.AiToolRegistry;
import com.web.labportalbackend.ai.service.AiUnifiedChatService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AiUnifiedChatServiceImpl implements AiUnifiedChatService {

    private static final String NO_AUTHORIZED_CAPABILITY =
            "I cannot find an available Lab Portal capability for this request.";

    private final AiToolCandidateCatalog candidateCatalog;
    private final AiToolPlanningClient planningClient;
    private final AiToolRegistry toolRegistry;
    private final AiAssistantGatewayService assistantGatewayService;
    private final AiActionSuggestionService actionSuggestionService;
    private final ObjectMapper objectMapper;

    public AiUnifiedChatServiceImpl(AiToolCandidateCatalog candidateCatalog,
                                    AiToolPlanningClient planningClient,
                                    AiToolRegistry toolRegistry,
                                    AiAssistantGatewayService assistantGatewayService,
                                    AiActionSuggestionService actionSuggestionService,
                                    ObjectMapper objectMapper) {
        this.candidateCatalog = candidateCatalog;
        this.planningClient = planningClient;
        this.toolRegistry = toolRegistry;
        this.assistantGatewayService = assistantGatewayService;
        this.actionSuggestionService = actionSuggestionService;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiUnifiedChatResponse chat(AiUnifiedChatRequest request, String requestId) {
        if (request == null || request.getInput() == null || request.getInput().isBlank()) {
            throw new IllegalArgumentException("Unified chat input is required");
        }
        String normalizedRequestId = AiGatewayRequest.normalizeRequestId(requestId);
        List<AiToolCandidate> candidates = List.copyOf(candidateCatalog.candidates());
        if (candidates.isEmpty()) {
            return nonTool(AiUnifiedChatResponseType.REFUSED, NO_AUTHORIZED_CAPABILITY, 0, 0);
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("input", request.getInput());
        ArrayNode candidateNodes = payload.putArray("candidates");
        candidates.forEach(candidate -> candidateNodes.add(candidate.toPlanningCandidate(objectMapper)));
        AiToolPlanningResponse planning = planningClient.plan(new AiGatewayRequest(payload, normalizedRequestId));
        if (planning.decision() != AiToolPlanningDecision.TOOL_REQUEST) {
            return nonTool(planning.decision() == AiToolPlanningDecision.CLARIFICATION
                            ? AiUnifiedChatResponseType.CLARIFICATION_REQUIRED : AiUnifiedChatResponseType.REFUSED,
                    planning.message(), planning.promptTokens(), planning.completionTokens());
        }

        AiToolCandidate selected = candidates.stream()
                .filter(candidate -> candidate.toCanonicalToolRequest(objectMapper).equals(planning.toolRequest()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("AI planner returned a non-canonical tool request"));
        AiToolDefinition definition = toolRegistry.get(selected.toolId());
        if (definition == null || definition.capability().domain() != selected.assistantKey().domain()
                || !definition.schemaVersion().equals(selected.schemaVersion())) {
            throw new IllegalArgumentException("AI planner selected an unavailable tool");
        }

        AiAssistantChatRequest delegated = new AiAssistantChatRequest();
        delegated.setInput(request.getInput());
        delegated.setCapability(definition.capability());
        delegated.setResourceId(selected.resource().resourceId());
        delegated.setParentResourceId(selected.parentResource() == null
                ? null : selected.parentResource().resourceId());
        AiAssistantChatResponse answer = assistantGatewayService.chat(
                selected.assistantKey(), delegated, normalizedRequestId);
        if (definition.capability() == AiCapability.LAB_SHIFT_CREATE_DRAFT) {
            AiActionPreviewResponse preview = actionSuggestionService.createLabShiftPreview(
                    selected.resource().resourceId(), answer);
            return new AiUnifiedChatResponse(AiUnifiedChatResponseType.ACTION_PREVIEW, answer.assistantKey(),
                    "Please review and confirm the proposed Lab time slot.",
                    Math.addExact(planning.promptTokens(), answer.promptTokens()),
                    Math.addExact(planning.completionTokens(), answer.completionTokens()), answer.citations(),
                    preview, null);
        }
        return new AiUnifiedChatResponse(AiUnifiedChatResponseType.ANSWER, answer.assistantKey(), answer.answer(),
                Math.addExact(planning.promptTokens(), answer.promptTokens()),
                Math.addExact(planning.completionTokens(), answer.completionTokens()), answer.citations());
    }

    private static AiUnifiedChatResponse nonTool(AiUnifiedChatResponseType type,
                                                  String message,
                                                  int promptTokens,
                                                  int completionTokens) {
        return new AiUnifiedChatResponse(type, null, message, promptTokens, completionTokens, List.of());
    }
}
