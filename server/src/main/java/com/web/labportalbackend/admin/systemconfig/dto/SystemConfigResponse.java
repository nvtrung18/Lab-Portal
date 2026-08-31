package com.web.labportalbackend.admin.systemconfig.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record SystemConfigResponse(
        @Schema(description = "Account registration and login controls") AccountConfig account,
        @Schema(description = "Laboratory lifecycle controls") LabConfig lab,
        @Schema(description = "Booking and check-in timing controls") BookingConfig booking,
        @Schema(description = "Report and product upload controls") UploadConfig upload,
        @Schema(description = "Research workflow controls") ResearchConfig research,
        @Schema(description = "AI availability and quota controls") AiConfig ai,
        @Schema(description = "Face verification thresholds and availability") FaceConfig face,
        @Schema(description = "QR fallback availability and token lifetime") QrFallbackConfig qrFallback,
        @Schema(description = "Notification availability and delivery controls") NotificationConfig notification,
        @Schema(description = "Retention periods for operational records") RetentionConfig retention,
        @Schema(description = "Operational log presentation limits") OperationalConfig operational
) {
    public SystemConfigResponse(AccountConfig account, LabConfig lab, BookingConfig booking,
                                UploadConfig upload, ResearchConfig research) {
        this(account, lab, booking, upload, research, null, null, null, null, null, null);
    }

    public record AccountConfig(
            @Schema(description = "Whether registration requires email verification") boolean requireEmailVerification,
            @Schema(description = "Role assigned after successful registration") String defaultRegisterRole,
            @Schema(description = "Failed login attempts allowed before enforcement") int maxLoginAttempts) {}

    public record LabConfig(
            @Schema(description = "Whether one manager may manage only one laboratory") boolean oneManagerOneLab,
            @Schema(description = "Whether inactive laboratories are hidden from students") boolean hideInactiveLabsFromStudent,
            @Schema(description = "Whether applications are disabled for inactive laboratories") boolean disableApplyForInactiveLab,
            @Schema(description = "Whether bookings are disabled for inactive laboratories") boolean disableBookingForInactiveLab) {}

    public record BookingConfig(
            @Schema(description = "Minutes after booking start during which check-in is allowed") int checkinWindowMinutes,
            @Schema(description = "Minimum minutes before start required for cancellation") int cancelBeforeMinutes,
            @Schema(description = "Whether past slots are hidden") boolean hidePastSlots,
            @Schema(description = "Whether cancelled slots are hidden") boolean hideCancelledSlots) {}

    public record UploadConfig(
            @Schema(description = "Maximum report file size in megabytes") int reportMaxSizeMb,
            @Schema(description = "Maximum product file size in megabytes") int productMaxSizeMb,
            @Schema(description = "Allowed report filename extensions") List<String> reportAllowedTypes,
            @Schema(description = "Allowed product filename extensions") List<String> productAllowedTypes) {}

    public record ResearchConfig(
            @Schema(description = "Maximum score accepted for research evaluation") int evaluationMaxScore,
            @Schema(description = "Whether a task requires an approved report before completion") boolean requireApprovedReportBeforeTaskDone,
            @Schema(description = "Whether leader review is required before manager review") boolean requireLeaderReviewBeforeManagerReview,
            @Schema(description = "Whether members may upload personal products") boolean allowMemberPersonalProductUpload) {}

    public record AiConfig(
            @Schema(description = "Whether AI assistant features are enabled") boolean enabled,
            @Schema(description = "Maximum AI requests allowed per user per day") int maxRequestsPerDay,
            @Schema(description = "Maximum context token budget accepted per request") int maxContextTokens) {}

    public record FaceConfig(
            @Schema(description = "Whether face check-in is enabled") boolean enabled,
            @Schema(description = "Minimum face match confidence between zero and one") double confidenceThreshold,
            @Schema(description = "Minimum liveness confidence between zero and one") double livenessThreshold) {}

    public record QrFallbackConfig(
            @Schema(description = "Whether QR fallback check-in is enabled") boolean enabled,
            @Schema(description = "Lifetime in seconds of a QR fallback token") int tokenTtlSeconds) {}

    public record NotificationConfig(
            @Schema(description = "Whether user notifications are enabled") boolean enabled,
            @Schema(description = "Maximum notifications returned in one page") int maxPageSize) {}

    public record RetentionConfig(
            @Schema(description = "Days to retain notifications") int notificationDays,
            @Schema(description = "Days to retain AI usage logs") int aiUsageLogDays,
            @Schema(description = "Days to retain face check-in logs") int faceCheckinLogDays,
            @Schema(description = "Days to retain security audit logs") int auditLogDays) {}

    public record OperationalConfig(
            @Schema(description = "Maximum operational log entries returned in one page") int maxPageSize,
            @Schema(description = "Whether bounded face failure reason codes may be returned") boolean includeFailureReasonCodes) {}
}
