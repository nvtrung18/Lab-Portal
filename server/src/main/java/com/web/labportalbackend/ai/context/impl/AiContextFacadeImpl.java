package com.web.labportalbackend.ai.context.impl;

import com.web.labportalbackend.ai.context.AiAuthorizedContext;
import com.web.labportalbackend.ai.context.AiContextBuildRequest;
import com.web.labportalbackend.ai.context.AiContextFacade;
import com.web.labportalbackend.ai.context.AiContextReadDeniedException;
import com.web.labportalbackend.ai.service.AiCapabilityDecision;
import com.web.labportalbackend.ai.service.AiCapabilityRequest;
import com.web.labportalbackend.ai.service.AiCapabilityResolver;
import org.springframework.stereotype.Service;

/** Non-transactional preflight boundary; all context reads occur in the fresh executor proxy. */
@Service
public class AiContextFacadeImpl implements AiContextFacade {

    private final AiCapabilityResolver capabilityResolver;
    private final AiContextFreshReadExecutor freshReadExecutor;

    public AiContextFacadeImpl(AiCapabilityResolver capabilityResolver,
                               AiContextFreshReadExecutor freshReadExecutor) {
        if (capabilityResolver == null || freshReadExecutor == null) {
            throw new IllegalArgumentException("resolver and fresh read executor are required");
        }
        this.capabilityResolver = capabilityResolver;
        this.freshReadExecutor = freshReadExecutor;
    }

    @Override
    public AiAuthorizedContext build(AiContextBuildRequest request) {
        if (request == null || request.capabilityRequest() == null) {
            throw new IllegalArgumentException("context build request is required");
        }
        AiCapabilityRequest capabilityRequest = request.capabilityRequest();
        AiCapabilityDecision preliminary = capabilityResolver.requireAllowed(capabilityRequest);
        if (!AiContextFreshReadExecutor.isValidDecision(preliminary, capabilityRequest)) {
            throw new AiContextReadDeniedException();
        }
        return freshReadExecutor.execute(preliminary, capabilityRequest, request.requestId());
    }
}
