package com.web.labportalbackend.ai.context;

import com.web.labportalbackend.research.enums.ReportStatus;

/** Permission-filtered report content supplied only for a selected report-review draft. */
public record AiResearchReportContext(
        Long id,
        Long projectId,
        Long groupId,
        Long milestoneId,
        Long taskId,
        Integer version,
        String title,
        String contentDone,
        String result,
        String difficulty,
        String nextPlan,
        String selfAssessment,
        String evidenceLink,
        ReportStatus status) {
}
