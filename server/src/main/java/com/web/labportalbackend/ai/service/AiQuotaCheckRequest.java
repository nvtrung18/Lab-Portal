package com.web.labportalbackend.ai.service;

import com.web.labportalbackend.ai.enums.AiAssistantKey;

/**
 * Trusted-server-only quota input. Future adapters must not accept these keys directly from clients.
 */
public record AiQuotaCheckRequest(Long userId, AiAssistantKey assistantKey, String role, String module,
                                  int contextTokens) {

    public AiQuotaCheckRequest {
        if (userId == null || assistantKey == null) {
            throw new IllegalArgumentException("userId and assistantKey are required");
        }
        if (role == null || role.isBlank() || module == null || module.isBlank()) {
            throw new IllegalArgumentException("role and module are required");
        }
        if (contextTokens < 0) {
            throw new IllegalArgumentException("contextTokens must not be negative");
        }
    }
}
