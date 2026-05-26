package com.web.labportalbackend.research.enums;

public enum TaskStatus {
    TODO,
    DOING,
    WAITING_REVIEW,
    NEEDS_REVISION,
    DONE,
    OVERDUE,
    CANCELLED,
    /**
     * Legacy values retained while older records and feature tests are migrated.
     */
    IN_PROGRESS,
    REVIEW
}
