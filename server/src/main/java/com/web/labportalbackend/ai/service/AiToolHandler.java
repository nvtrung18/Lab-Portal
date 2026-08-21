package com.web.labportalbackend.ai.service;

import com.web.labportalbackend.ai.context.AiAuthorizedContext;
import com.web.labportalbackend.ai.context.AiDomainContext;
import com.web.labportalbackend.ai.enums.AiToolId;
import java.util.Set;

/** Trusted Spring handlers register exact canonical IDs; model input never selects an implementation. */
public interface AiToolHandler {
    Set<AiToolId> supportedToolIds();

    AiDomainContext execute(AiToolId toolId, AiAuthorizedContext authorizedContext);
}
