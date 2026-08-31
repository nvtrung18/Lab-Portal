package com.web.labportalbackend.ai.rag.service;

import com.web.labportalbackend.ai.context.AiAuthorizedContext;
import com.web.labportalbackend.ai.enums.AiAssistantSystemRole;
import com.web.labportalbackend.ai.service.AiAssistantProfile;

public interface AiRagRetrievalService {

    AiAuthorizedRetrieval retrieve(AiAssistantProfile profile,
                                   Long actorId,
                                   AiAssistantSystemRole selectedRole,
                                   AiAuthorizedContext authorizedContext,
                                   String query);
}
