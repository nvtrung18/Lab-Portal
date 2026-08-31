package com.web.labportalbackend.admin.systemconfig.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.labportalbackend.admin.systemconfig.dto.SystemConfigRequest;
import com.web.labportalbackend.admin.systemconfig.dto.SystemConfigResponse;
import com.web.labportalbackend.admin.systemconfig.entity.SystemConfigEntity;
import com.web.labportalbackend.admin.audit.enums.AuditAction;
import com.web.labportalbackend.admin.audit.enums.AuditModule;
import com.web.labportalbackend.admin.audit.service.AuditLogService;
import com.web.labportalbackend.admin.systemconfig.repository.SystemConfigRepository;
import com.web.labportalbackend.admin.systemconfig.service.SystemConfigService;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SystemConfigServiceImpl implements SystemConfigService {

    private static final String GLOBAL_CONFIG_KEY = "GLOBAL_SYSTEM_CONFIG";
    private static final String UPDATE_DESCRIPTION = "Admin đã cập nhật cấu hình hệ thống.";

    private final SystemConfigRepository systemConfigRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public SystemConfigResponse getConfig() {
        return systemConfigRepository.findByConfigKeyAndDeletedFalse(GLOBAL_CONFIG_KEY)
                .map(SystemConfigEntity::getConfigValueJson)
                .map(this::readConfig)
                .orElseGet(this::defaultConfig);
    }

    @Override
    @Transactional(readOnly = true)
    public SystemConfigResponse getConfigForStatusAuthorization() {
        return systemConfigRepository.findByConfigKeyForStatusAuthorization(GLOBAL_CONFIG_KEY)
                .map(SystemConfigEntity::getConfigValueJson)
                .map(this::readConfig)
                .orElseGet(this::defaultConfig);
    }

    @Override
    @Transactional
    public SystemConfigResponse updateConfig(SystemConfigRequest request) {
        validateRequestShape(request);
        SystemConfigResponse config = toResponse(request);
        validate(config);

        User currentUser = getCurrentUser();
        SystemConfigEntity entity = systemConfigRepository.findByConfigKeyAndDeletedFalse(GLOBAL_CONFIG_KEY)
                .orElseGet(this::newGlobalConfigEntity);
        entity.setConfigValueJson(writeConfig(config));
        entity.setUpdatedBy(currentUser);
        systemConfigRepository.save(entity);

        auditLogService.log(
                currentUser,
                AuditAction.UPDATE_SYSTEM_CONFIG,
                AuditModule.SYSTEM_CONFIG,
                "SYSTEM_CONFIG",
                entity.getId(),
                UPDATE_DESCRIPTION
        );

        return config;
    }

    private SystemConfigEntity newGlobalConfigEntity() {
        SystemConfigEntity entity = new SystemConfigEntity();
        entity.setConfigKey(GLOBAL_CONFIG_KEY);
        return entity;
    }

    private SystemConfigResponse toResponse(SystemConfigRequest request) {
        return new SystemConfigResponse(
                new SystemConfigResponse.AccountConfig(
                        request.getAccount().isRequireEmailVerification(),
                        normalizeRole(request.getAccount().getDefaultRegisterRole()),
                        request.getAccount().getMaxLoginAttempts()
                ),
                new SystemConfigResponse.LabConfig(
                        request.getLab().isOneManagerOneLab(),
                        request.getLab().isHideInactiveLabsFromStudent(),
                        request.getLab().isDisableApplyForInactiveLab(),
                        request.getLab().isDisableBookingForInactiveLab()
                ),
                new SystemConfigResponse.BookingConfig(
                        request.getBooking().getCheckinWindowMinutes(),
                        request.getBooking().getCancelBeforeMinutes(),
                        request.getBooking().isHidePastSlots(),
                        request.getBooking().isHideCancelledSlots()
                ),
                new SystemConfigResponse.UploadConfig(
                        request.getUpload().getReportMaxSizeMb(),
                        request.getUpload().getProductMaxSizeMb(),
                        normalizeAllowedTypes(request.getUpload().getReportAllowedTypes()),
                        normalizeAllowedTypes(request.getUpload().getProductAllowedTypes())
                ),
                new SystemConfigResponse.ResearchConfig(
                        request.getResearch().getEvaluationMaxScore(),
                        request.getResearch().isRequireApprovedReportBeforeTaskDone(),
                        request.getResearch().isRequireLeaderReviewBeforeManagerReview(),
                        request.getResearch().isAllowMemberPersonalProductUpload()
                ),
                new SystemConfigResponse.AiConfig(request.getAi().isEnabled(),
                        request.getAi().getMaxRequestsPerDay(), request.getAi().getMaxContextTokens()),
                new SystemConfigResponse.FaceConfig(request.getFace().isEnabled(),
                        request.getFace().getConfidenceThreshold(), request.getFace().getLivenessThreshold()),
                new SystemConfigResponse.QrFallbackConfig(request.getQrFallback().isEnabled(),
                        request.getQrFallback().getTokenTtlSeconds()),
                new SystemConfigResponse.NotificationConfig(request.getNotification().isEnabled(),
                        request.getNotification().getMaxPageSize()),
                new SystemConfigResponse.RetentionConfig(request.getRetention().getNotificationDays(),
                        request.getRetention().getAiUsageLogDays(), request.getRetention().getFaceCheckinLogDays(),
                        request.getRetention().getAuditLogDays()),
                new SystemConfigResponse.OperationalConfig(request.getOperational().getMaxPageSize(),
                        request.getOperational().isIncludeFailureReasonCodes())
        );
    }

    private void validate(SystemConfigResponse config) {
        List<String> errors = new ArrayList<>();

        if (!"STUDENT".equals(config.account().defaultRegisterRole())) {
            errors.add("defaultRegisterRole chỉ được phép là STUDENT.");
        }
        if (config.account().maxLoginAttempts() < 1) {
            errors.add("maxLoginAttempts phải lớn hơn hoặc bằng 1.");
        }
        if (config.booking().checkinWindowMinutes() <= 0) {
            errors.add("checkinWindowMinutes phải lớn hơn 0.");
        }
        if (config.booking().cancelBeforeMinutes() < 0) {
            errors.add("cancelBeforeMinutes phải lớn hơn hoặc bằng 0.");
        }
        if (config.upload().reportMaxSizeMb() <= 0 || config.upload().reportMaxSizeMb() > 50) {
            errors.add("reportMaxSizeMb phải lớn hơn 0 và không vượt quá 50.");
        }
        if (config.upload().productMaxSizeMb() <= 0 || config.upload().productMaxSizeMb() > 200) {
            errors.add("productMaxSizeMb phải lớn hơn 0 và không vượt quá 200.");
        }
        if (config.upload().reportAllowedTypes().isEmpty()) {
            errors.add("reportAllowedTypes không được rỗng.");
        }
        if (config.upload().productAllowedTypes().isEmpty()) {
            errors.add("productAllowedTypes không được rỗng.");
        }
        if (config.research().evaluationMaxScore() <= 0) {
            errors.add("evaluationMaxScore phải lớn hơn 0.");
        }

        if (config.ai().maxRequestsPerDay() < 1 || config.ai().maxContextTokens() < 1) {
            errors.add("AI quota and context limits must be greater than zero.");
        }
        if (!validThreshold(config.face().confidenceThreshold())
                || !validThreshold(config.face().livenessThreshold())) {
            errors.add("Face confidence and liveness thresholds must be between 0 and 1.");
        }
        if (config.qrFallback().tokenTtlSeconds() < 1 || config.qrFallback().tokenTtlSeconds() > 86400) {
            errors.add("QR fallback token TTL must be between 1 and 86400 seconds.");
        }
        if (config.notification().maxPageSize() < 1 || config.notification().maxPageSize() > 100) {
            errors.add("Notification page size must be between 1 and 100.");
        }
        if (config.operational().maxPageSize() < 1 || config.operational().maxPageSize() > 100) {
            errors.add("Operational log page size must be between 1 and 100.");
        }
        if (!validRetention(config.retention().notificationDays())
                || !validRetention(config.retention().aiUsageLogDays())
                || !validRetention(config.retention().faceCheckinLogDays())
                || !validRetention(config.retention().auditLogDays())) {
            errors.add("Retention periods must be between 1 and 3650 days.");
        }

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join(" ", errors));
        }
    }

    private SystemConfigResponse readConfig(String configValueJson) {
        try {
            SystemConfigResponse config = objectMapper.readValue(configValueJson, SystemConfigResponse.class);
            if (!hasCompleteSections(config)) {
                return defaultConfig();
            }
            config = completeOperationalSections(config);
            validate(config);
            return config;
        } catch (JsonProcessingException | IllegalArgumentException | NullPointerException ex) {
            return defaultConfig();
        }
    }

    private boolean hasCompleteSections(SystemConfigResponse config) {
        return config != null
                && config.account() != null
                && config.lab() != null
                && config.booking() != null
                && config.upload() != null
                && config.research() != null;
    }

    private SystemConfigResponse completeOperationalSections(SystemConfigResponse config) {
        SystemConfigResponse defaults = defaultConfig();
        return new SystemConfigResponse(config.account(), config.lab(), config.booking(), config.upload(),
                config.research(), config.ai() == null ? defaults.ai() : config.ai(),
                config.face() == null ? defaults.face() : config.face(),
                config.qrFallback() == null ? defaults.qrFallback() : config.qrFallback(),
                config.notification() == null ? defaults.notification() : config.notification(),
                config.retention() == null ? defaults.retention() : config.retention(),
                config.operational() == null ? defaults.operational() : config.operational());
    }

    private String writeConfig(SystemConfigResponse config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Không thể lưu cấu hình hệ thống.");
        }
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new AccessDeniedException("Authentication is required");
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new AccessDeniedException("Authenticated user was not found"));
    }

    private String normalizeRole(String role) {
        return role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
    }

    private List<String> normalizeAllowedTypes(List<String> values) {
        if (values == null) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String item = value.trim().toLowerCase(Locale.ROOT);
            if (!item.isBlank()) {
                normalized.add(item);
            }
        }
        return List.copyOf(normalized);
    }

    private boolean validThreshold(double value) {
        return !Double.isNaN(value) && value >= 0.0 && value <= 1.0;
    }

    private boolean validRetention(int days) {
        return days >= 1 && days <= 3650;
    }

    private void validateRequestShape(SystemConfigRequest request) {
        if (request == null || !request.getUnknownFields().isEmpty()) {
            throw new IllegalArgumentException("Unknown or missing system config fields are not allowed");
        }
        List<SystemConfigRequest.StrictSection> sections = java.util.Arrays.asList(
                request.getAccount(), request.getLab(), request.getBooking(), request.getUpload(),
                request.getResearch(), request.getAi(), request.getFace(), request.getQrFallback(),
                request.getNotification(), request.getRetention(), request.getOperational());
        if (sections.stream().anyMatch(section -> section == null || !section.getUnknownFields().isEmpty())) {
            throw new IllegalArgumentException("Unknown system config fields, including secret values, are not allowed");
        }
    }

    private SystemConfigResponse defaultConfig() {
        return new SystemConfigResponse(
                new SystemConfigResponse.AccountConfig(true, "STUDENT", 5),
                new SystemConfigResponse.LabConfig(true, true, true, true),
                new SystemConfigResponse.BookingConfig(10, 30, true, true),
                new SystemConfigResponse.UploadConfig(
                        10,
                        50,
                        List.of("pdf", "doc", "docx"),
                        List.of("pdf", "doc", "docx", "ppt", "pptx", "zip", "mp4")
                ),
                new SystemConfigResponse.ResearchConfig(10, true, true, true),
                new SystemConfigResponse.AiConfig(true, 100, 8192),
                new SystemConfigResponse.FaceConfig(true, 0.80, 0.80),
                new SystemConfigResponse.QrFallbackConfig(true, 300),
                new SystemConfigResponse.NotificationConfig(true, 100),
                new SystemConfigResponse.RetentionConfig(90, 180, 180, 365),
                new SystemConfigResponse.OperationalConfig(100, true)
        );
    }
}
