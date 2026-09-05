package com.web.labportalbackend.ai.controller;

import com.web.labportalbackend.ai.dto.request.AiUnifiedChatRequest;
import com.web.labportalbackend.ai.dto.response.AiUnifiedChatResponse;
import com.web.labportalbackend.ai.dto.response.AiActionResultResponse;
import com.web.labportalbackend.ai.service.AiActionSuggestionService;
import com.web.labportalbackend.ai.service.AiUnifiedChatService;
import com.web.labportalbackend.common.dto.Response;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiUnifiedChatController {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    private final AiUnifiedChatService unifiedChatService;
    private final AiActionSuggestionService actionSuggestionService;

    @PostMapping("/chat")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Response<AiUnifiedChatResponse>> chat(
            @Valid @RequestBody AiUnifiedChatRequest request,
            @RequestHeader(name = REQUEST_ID_HEADER, required = false) String requestId) {
        return ResponseEntity.ok(Response.ok("Assistant response generated successfully",
                unifiedChatService.chat(request, requestId)));
    }

    @PostMapping("/actions/{suggestionId}/confirm")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Response<AiActionResultResponse>> confirm(@PathVariable Long suggestionId) {
        return ResponseEntity.ok(Response.ok("AI action confirmed and executed successfully",
                actionSuggestionService.confirm(suggestionId)));
    }

    @PostMapping("/actions/{suggestionId}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Response<AiActionResultResponse>> cancel(@PathVariable Long suggestionId) {
        return ResponseEntity.ok(Response.ok("AI action preview cancelled successfully",
                actionSuggestionService.cancel(suggestionId)));
    }
}
