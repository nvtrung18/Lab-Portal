package com.web.labportalbackend.ai.enums;

public enum AiCapability {
    ADMIN_SYSTEM_SUMMARY(AiAssistantDomain.ADMIN, AiResourceType.SYSTEM, null,
            AiRequestedAction.READ, AiActionRiskBoundary.READ_ONLY),
    ADMIN_AUDIT_SUMMARY(AiAssistantDomain.ADMIN, AiResourceType.AUDIT_LOG, null,
            AiRequestedAction.READ, AiActionRiskBoundary.READ_ONLY),
    ADMIN_USER_STATUS_LOOKUP(AiAssistantDomain.ADMIN, AiResourceType.USER_ACCOUNT, null,
            AiRequestedAction.READ, AiActionRiskBoundary.READ_ONLY),
    ADMIN_CONFIG_DRAFT(AiAssistantDomain.ADMIN, AiResourceType.SYSTEM_CONFIG, null,
            AiRequestedAction.DRAFT, AiActionRiskBoundary.DRAFT_ONLY),
    ADMIN_ACCOUNT_ACTION_DRAFT(AiAssistantDomain.ADMIN, AiResourceType.USER_ACCOUNT, null,
            AiRequestedAction.DRAFT, AiActionRiskBoundary.DRAFT_ONLY),

    LAB_POLICY_READ(AiAssistantDomain.LAB, AiResourceType.LABORATORY, null,
            AiRequestedAction.READ, AiActionRiskBoundary.READ_ONLY),
    LAB_SLOT_READ(AiAssistantDomain.LAB, AiResourceType.TIME_SLOT, null,
            AiRequestedAction.READ, AiActionRiskBoundary.READ_ONLY),
    LAB_AVAILABLE_SLOTS_READ(AiAssistantDomain.LAB, AiResourceType.LABORATORY, null,
            AiRequestedAction.READ, AiActionRiskBoundary.READ_ONLY),
    LAB_OWN_BOOKING_READ(AiAssistantDomain.LAB, AiResourceType.BOOKING, null,
            AiRequestedAction.READ, AiActionRiskBoundary.READ_ONLY),
    LAB_MANAGED_SUMMARY(AiAssistantDomain.LAB, AiResourceType.LABORATORY, null,
            AiRequestedAction.READ, AiActionRiskBoundary.READ_ONLY),
    LAB_SHIFT_CREATE_DRAFT(AiAssistantDomain.LAB, AiResourceType.LABORATORY, null,
            AiRequestedAction.DRAFT, AiActionRiskBoundary.DRAFT_ONLY),
    LAB_BOOKING_DRAFT(AiAssistantDomain.LAB, AiResourceType.TIME_SLOT, null,
            AiRequestedAction.DRAFT, AiActionRiskBoundary.DRAFT_ONLY),
    LAB_CHECKIN_GUIDANCE(AiAssistantDomain.LAB, AiResourceType.BOOKING, null,
            AiRequestedAction.READ, AiActionRiskBoundary.READ_ONLY),

    RESEARCH_PROJECT_SUMMARY(AiAssistantDomain.RESEARCH, AiResourceType.PROJECT, null,
            AiRequestedAction.READ, AiActionRiskBoundary.READ_ONLY),
    RESEARCH_GROUP_SUMMARY(AiAssistantDomain.RESEARCH, AiResourceType.GROUP, null,
            AiRequestedAction.READ, AiActionRiskBoundary.READ_ONLY),
    RESEARCH_ASSIGNED_TASK_READ(AiAssistantDomain.RESEARCH, AiResourceType.TASK, null,
            AiRequestedAction.READ, AiActionRiskBoundary.READ_ONLY),
    RESEARCH_TASK_PROPOSAL_DRAFT(AiAssistantDomain.RESEARCH, AiResourceType.GROUP, AiResourceType.PROJECT,
            AiRequestedAction.DRAFT, AiActionRiskBoundary.DRAFT_ONLY),
    RESEARCH_TASK_SUGGESTION_DRAFT(AiAssistantDomain.RESEARCH, AiResourceType.TASK, null,
            AiRequestedAction.DRAFT, AiActionRiskBoundary.DRAFT_ONLY),
    RESEARCH_REPORT_REVIEW_DRAFT(AiAssistantDomain.RESEARCH, AiResourceType.REPORT, null,
            AiRequestedAction.DRAFT, AiActionRiskBoundary.DRAFT_ONLY);

    private final AiAssistantDomain domain;
    private final AiResourceType resourceType;
    private final AiResourceType parentResourceType;
    private final AiRequestedAction action;
    private final AiActionRiskBoundary riskBoundary;

    AiCapability(AiAssistantDomain domain, AiResourceType resourceType, AiResourceType parentResourceType,
                 AiRequestedAction action, AiActionRiskBoundary riskBoundary) {
        this.domain = domain;
        this.resourceType = resourceType;
        this.parentResourceType = parentResourceType;
        this.action = action;
        this.riskBoundary = riskBoundary;
    }

    public AiAssistantDomain domain() { return domain; }
    public AiResourceType resourceType() { return resourceType; }
    public AiResourceType parentResourceType() { return parentResourceType; }
    public AiRequestedAction action() { return action; }
    public AiActionRiskBoundary riskBoundary() { return riskBoundary; }
}
