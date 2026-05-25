package com.web.labportalbackend.research.enums;

public enum MilestoneStatus {
    NOT_STARTED,
    IN_PROGRESS,
    WAITING_REVIEW,
    COMPLETED,
    OVERDUE,
    CANCELLED,
    /**
     * Legacy values retained for source compatibility while stored records are migrated.
     */
    PLANNED,
    DELAYED
}
