package com.web.labportalbackend.ai.service;

import com.web.labportalbackend.ai.enums.AiActionRiskBoundary;

public enum AiAuditGateStatus {
    NOT_CLASSIFIED,
    NOT_REQUIRED,
    DRAFT_ONLY_WRITE_BLOCKED,
    CONFIRMATION_REQUIRED,
    APPROVAL_REQUIRED,
    PROHIBITED;

    public static AiAuditGateStatus from(AiActionRiskBoundary riskBoundary) {
        if (riskBoundary == null) {
            return NOT_CLASSIFIED;
        }
        return switch (riskBoundary) {
            case READ_ONLY -> NOT_REQUIRED;
            case DRAFT_ONLY -> DRAFT_ONLY_WRITE_BLOCKED;
            case CONFIRM_REQUIRED -> CONFIRMATION_REQUIRED;
            case APPROVAL_REQUIRED -> APPROVAL_REQUIRED;
            case PROHIBITED -> PROHIBITED;
        };
    }
}
