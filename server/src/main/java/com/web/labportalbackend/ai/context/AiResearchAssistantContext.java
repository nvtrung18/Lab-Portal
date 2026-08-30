package com.web.labportalbackend.ai.context;

import com.web.labportalbackend.ai.service.AiResearchContext;

/** New bounded facade payload; the legacy research context service remains unchanged. */
public record AiResearchAssistantContext(
        AiResearchContext research,
        AiBoundedList<AiResearchContext.Group> groups,
        AiBoundedList<AiResearchContext.Milestone> milestones,
        AiBoundedList<AiResearchContext.Task> tasks,
        AiResearchReportContext report,
        Long selectedResourceId,
        boolean draftOnly) implements AiDomainContext {
}
