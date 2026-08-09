package com.web.labportalbackend.ai.context;

/** Closed marker for minimized, immutable domain contexts. */
public sealed interface AiDomainContext permits AiAdminContext, AiLabContext, AiResearchAssistantContext {
}
