package com.web.labportalbackend.admin.systemconfig.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SystemConfigRequest {

    @Valid @NotNull @Schema(description = "Account registration and login controls") private AccountConfig account;
    @Valid @NotNull @Schema(description = "Laboratory lifecycle controls") private LabConfig lab;
    @Valid @NotNull @Schema(description = "Booking and check-in timing controls") private BookingConfig booking;
    @Valid @NotNull @Schema(description = "Report and product upload controls") private UploadConfig upload;
    @Valid @NotNull @Schema(description = "Research workflow controls") private ResearchConfig research;
    @Valid @NotNull @Schema(description = "AI availability and quota controls") private AiConfig ai;
    @Valid @NotNull @Schema(description = "Face verification thresholds and availability") private FaceConfig face;
    @Valid @NotNull @Schema(description = "QR fallback availability and token lifetime") private QrFallbackConfig qrFallback;
    @Valid @NotNull @Schema(description = "Notification availability and delivery controls") private NotificationConfig notification;
    @Valid @NotNull @Schema(description = "Retention periods for operational records") private RetentionConfig retention;
    @Valid @NotNull @Schema(description = "Operational log presentation limits") private OperationalConfig operational;

    @JsonIgnore private final Map<String, JsonNode> unknownFields = new LinkedHashMap<>();

    @JsonAnySetter
    public void captureUnknown(String name, JsonNode value) { unknownFields.put(name, value); }

    @Data @NoArgsConstructor
    public static class AccountConfig extends StrictSection {
        @Schema(description = "Whether registration requires email verification") private boolean requireEmailVerification;
        @NotNull @Schema(description = "Role assigned after successful registration") private String defaultRegisterRole;
        @Schema(description = "Failed login attempts allowed before enforcement") private int maxLoginAttempts;
    }

    @Data @NoArgsConstructor
    public static class LabConfig extends StrictSection {
        @Schema(description = "Whether one manager may manage only one laboratory") private boolean oneManagerOneLab;
        @Schema(description = "Whether inactive laboratories are hidden from students") private boolean hideInactiveLabsFromStudent;
        @Schema(description = "Whether applications are disabled for inactive laboratories") private boolean disableApplyForInactiveLab;
        @Schema(description = "Whether bookings are disabled for inactive laboratories") private boolean disableBookingForInactiveLab;
    }

    @Data @NoArgsConstructor
    public static class BookingConfig extends StrictSection {
        @Schema(description = "Minutes after booking start during which check-in is allowed") private int checkinWindowMinutes;
        @Schema(description = "Minimum minutes before start required for cancellation") private int cancelBeforeMinutes;
        @Schema(description = "Whether past slots are hidden") private boolean hidePastSlots;
        @Schema(description = "Whether cancelled slots are hidden") private boolean hideCancelledSlots;
    }

    @Data @NoArgsConstructor
    public static class UploadConfig extends StrictSection {
        @Schema(description = "Maximum report file size in megabytes") private int reportMaxSizeMb;
        @Schema(description = "Maximum product file size in megabytes") private int productMaxSizeMb;
        @NotNull @Schema(description = "Allowed report filename extensions") private List<String> reportAllowedTypes;
        @NotNull @Schema(description = "Allowed product filename extensions") private List<String> productAllowedTypes;
    }

    @Data @NoArgsConstructor
    public static class ResearchConfig extends StrictSection {
        @Schema(description = "Maximum score accepted for research evaluation") private int evaluationMaxScore;
        @Schema(description = "Whether a task requires an approved report before completion") private boolean requireApprovedReportBeforeTaskDone;
        @Schema(description = "Whether leader review is required before manager review") private boolean requireLeaderReviewBeforeManagerReview;
        @Schema(description = "Whether members may upload personal products") private boolean allowMemberPersonalProductUpload;
    }

    @Data @NoArgsConstructor
    public static class AiConfig extends StrictSection {
        @Schema(description = "Whether AI assistant features are enabled") private boolean enabled;
        @Schema(description = "Maximum AI requests allowed per user per day") private int maxRequestsPerDay;
        @Schema(description = "Maximum context token budget accepted per request") private int maxContextTokens;
    }

    @Data @NoArgsConstructor
    public static class FaceConfig extends StrictSection {
        @Schema(description = "Whether face check-in is enabled") private boolean enabled;
        @Schema(description = "Minimum face match confidence between zero and one") private double confidenceThreshold;
        @Schema(description = "Minimum liveness confidence between zero and one") private double livenessThreshold;
    }

    @Data @NoArgsConstructor
    public static class QrFallbackConfig extends StrictSection {
        @Schema(description = "Whether QR fallback check-in is enabled") private boolean enabled;
        @Schema(description = "Lifetime in seconds of a QR fallback token") private int tokenTtlSeconds;
    }

    @Data @NoArgsConstructor
    public static class NotificationConfig extends StrictSection {
        @Schema(description = "Whether user notifications are enabled") private boolean enabled;
        @Schema(description = "Maximum notifications returned in one page") private int maxPageSize;
    }

    @Data @NoArgsConstructor
    public static class RetentionConfig extends StrictSection {
        @Schema(description = "Days to retain notifications") private int notificationDays;
        @Schema(description = "Days to retain AI usage logs") private int aiUsageLogDays;
        @Schema(description = "Days to retain face check-in logs") private int faceCheckinLogDays;
        @Schema(description = "Days to retain security audit logs") private int auditLogDays;
    }

    @Data @NoArgsConstructor
    public static class OperationalConfig extends StrictSection {
        @Schema(description = "Maximum operational log entries returned in one page") private int maxPageSize;
        @Schema(description = "Whether bounded face failure reason codes may be returned") private boolean includeFailureReasonCodes;
    }

    @Data
    public abstract static class StrictSection {
        @JsonIgnore private final Map<String, JsonNode> unknownFields = new LinkedHashMap<>();

        @JsonAnySetter
        public void captureUnknown(String name, JsonNode value) { unknownFields.put(name, value); }
    }
}
