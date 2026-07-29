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
                        request.account().requireEmailVerification(),
                        normalizeRole(request.account().defaultRegisterRole()),
                        request.account().maxLoginAttempts()
                ),
                new SystemConfigResponse.LabConfig(
                        request.lab().oneManagerOneLab(),
                        request.lab().hideInactiveLabsFromStudent(),
                        request.lab().disableApplyForInactiveLab(),
                        request.lab().disableBookingForInactiveLab()
                ),
                new SystemConfigResponse.BookingConfig(
                        request.booking().checkinWindowMinutes(),
                        request.booking().cancelBeforeMinutes(),
                        request.booking().hidePastSlots(),
                        request.booking().hideCancelledSlots()
                ),
                new SystemConfigResponse.UploadConfig(
                        request.upload().reportMaxSizeMb(),
                        request.upload().productMaxSizeMb(),
                        normalizeAllowedTypes(request.upload().reportAllowedTypes()),
                        normalizeAllowedTypes(request.upload().productAllowedTypes())
                ),
                new SystemConfigResponse.ResearchConfig(
                        request.research().evaluationMaxScore(),
                        request.research().requireApprovedReportBeforeTaskDone(),
                        request.research().requireLeaderReviewBeforeManagerReview(),
                        request.research().allowMemberPersonalProductUpload()
                )
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
                new SystemConfigResponse.ResearchConfig(10, true, true, true)
        );
    }
}
