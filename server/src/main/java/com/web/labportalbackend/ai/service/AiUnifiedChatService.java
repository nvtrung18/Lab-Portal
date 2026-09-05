package com.web.labportalbackend.ai.service;

import com.web.labportalbackend.ai.dto.request.AiUnifiedChatRequest;
import com.web.labportalbackend.ai.dto.response.AiUnifiedChatResponse;

public interface AiUnifiedChatService {
    AiUnifiedChatResponse chat(AiUnifiedChatRequest request, String requestId);
}
