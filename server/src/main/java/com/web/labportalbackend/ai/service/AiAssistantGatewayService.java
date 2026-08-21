package com.web.labportalbackend.ai.service;

import com.web.labportalbackend.ai.dto.request.AiAssistantChatRequest;
import com.web.labportalbackend.ai.dto.response.AiAssistantChatResponse;
import com.web.labportalbackend.ai.enums.AiAssistantKey;

public interface AiAssistantGatewayService {

    AiAssistantChatResponse chat(AiAssistantKey assistantKey, AiAssistantChatRequest request, String requestId);
}
