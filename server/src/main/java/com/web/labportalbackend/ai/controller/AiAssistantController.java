package com.web.labportalbackend.ai.controller;

import com.web.labportalbackend.ai.dto.request.AiAssistantChatRequest;
import com.web.labportalbackend.ai.dto.response.AiAssistantChatResponse;
import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.ai.service.AiAssistantGatewayService;
import com.web.labportalbackend.common.dto.Response;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai/assistants")
@RequiredArgsConstructor
public class AiAssistantController {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    private final AiAssistantGatewayService gatewayService;

    @PostMapping("/{assistantKey}/chat")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Response<AiAssistantChatResponse>> chat(
            @PathVariable AiAssistantKey assistantKey,
            @Valid @RequestBody AiAssistantChatRequest request,
            @RequestHeader(name = REQUEST_ID_HEADER, required = false) String requestId) {
        return ResponseEntity.ok(Response.ok("Assistant response generated successfully",
                gatewayService.chat(assistantKey, request, requestId)));
    }
}
