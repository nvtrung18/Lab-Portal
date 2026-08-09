package com.web.labportalbackend.ai.context;

public interface AiContextFacade {
    AiAuthorizedContext build(AiContextBuildRequest request);
}
