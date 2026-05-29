package com.web.labportalbackend.admin.systemconfig.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SystemConfigRequest(
        @Valid @NotNull AccountConfig account,
        @Valid @NotNull LabConfig lab,
        @Valid @NotNull BookingConfig booking,
        @Valid @NotNull UploadConfig upload,
        @Valid @NotNull ResearchConfig research
) {

    public record AccountConfig(
            boolean requireEmailVerification,
            @NotNull String defaultRegisterRole,
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
            @NotNull List<String> reportAllowedTypes,
            @NotNull List<String> productAllowedTypes
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
