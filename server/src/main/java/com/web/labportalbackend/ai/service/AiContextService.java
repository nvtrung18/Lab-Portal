package com.web.labportalbackend.ai.service;

/**
 * Builds a minimized research context from the authenticated server identity and
 * authorized project scope. It does not call an AI gateway or persist any data.
 */
public interface AiContextService {

    AiResearchContext buildResearchContext(AiResearchContextRequest request);
}
