package com.web.labportalbackend.admin.systemconfig.dto;

import java.util.List;

public record SystemConfigResponse(
        AccountConfig account,
        LabConfig lab,
        BookingConfig booking,
        UploadConfig upload,
        ResearchConfig research
) {

    public record AccountConfig(
            boolean requireEmailVerification,
            String defaultRegisterRole,
            int maxLoginAttempts
    ) {
    }

    public record LabConfig(
            boolean oneManagerOneLab,
            boolean hideInactiveLabsFromStudent,
            boolean disableApplyForInactiveLab,
            boolean disableBookingForInactiveLab
    ) {
    }

    public record BookingConfig(
            int checkinWindowMinutes,
            int cancelBeforeMinutes,
            boolean hidePastSlots,
            boolean hideCancelledSlots
    ) {
    }

    public record UploadConfig(
            int reportMaxSizeMb,
            int productMaxSizeMb,
            List<String> reportAllowedTypes,
            List<String> productAllowedTypes
    ) {
    }

    public record ResearchConfig(
            int evaluationMaxScore,
            boolean requireApprovedReportBeforeTaskDone,
            boolean requireLeaderReviewBeforeManagerReview,
            boolean allowMemberPersonalProductUpload
    ) {
    }
}
