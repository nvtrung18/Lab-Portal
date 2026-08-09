package com.web.labportalbackend.ai.context;

import com.web.labportalbackend.ai.service.AiCapabilityRequest;

/** Small internal-only envelope for an already server-originated capability request. */
public record AiContextBuildRequest(AiCapabilityRequest capabilityRequest, String requestId) {

    public AiContextBuildRequest {
        if (capabilityRequest == null) {
            throw new IllegalArgumentException("capabilityRequest is required");
        }
        if (requestId != null) {
            requestId = requestId.trim();
            if (requestId.isEmpty()) {
                throw new IllegalArgumentException("requestId must be nonblank when supplied");
            }
        }
    }
}
