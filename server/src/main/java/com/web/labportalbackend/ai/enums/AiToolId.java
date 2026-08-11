package com.web.labportalbackend.ai.enums;

/** Fixed, non-executable identifiers exposed by the P5A-T6 policy catalog. */
public enum AiToolId {
    ADMIN_SYSTEM_SUMMARY("admin.system.summary"),
    ADMIN_AUDIT_SUMMARY("admin.audit.summary"),
    ADMIN_USER_STATUS_LOOKUP("admin.user.status.lookup"),
    ADMIN_CONFIG_DRAFT("admin.config.draft"),
    ADMIN_ACCOUNT_ACTION_DRAFT("admin.account.action.draft"),
    LAB_POLICY_READ("lab.policy.read"),
    LAB_SLOT_READ("lab.slot.read"),
    LAB_OWN_BOOKING_READ("lab.own.booking.read"),
    LAB_MANAGED_SUMMARY("lab.managed.summary"),
    LAB_BOOKING_DRAFT("lab.booking.draft"),
    LAB_CHECKIN_GUIDANCE("lab.checkin.guidance"),
    RESEARCH_PROJECT_SUMMARY("research.project.summary"),
    RESEARCH_GROUP_SUMMARY("research.group.summary"),
    RESEARCH_ASSIGNED_TASK_READ("research.assigned.task.read"),
    RESEARCH_TASK_PROPOSAL_DRAFT("research.task.proposal.draft"),
    RESEARCH_TASK_SUGGESTION_DRAFT("research.task.suggestion.draft"),
    RESEARCH_REPORT_REVIEW_DRAFT("research.report.review.draft");

    private final String value;

    AiToolId(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
