package com.web.labportalbackend.ai.service;

import com.web.labportalbackend.ai.dto.response.AiActionPreviewResponse;
import com.web.labportalbackend.ai.dto.response.AiActionResultResponse;
import com.web.labportalbackend.ai.dto.response.AiAssistantChatResponse;

public interface AiActionSuggestionService {

    AiActionPreviewResponse createLabShiftPreview(Long authorizedLabId, AiAssistantChatResponse generated);

    AiActionResultResponse confirm(Long suggestionId);

    AiActionResultResponse cancel(Long suggestionId);
}
