package com.web.labportalbackend.admin.systemconfig.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.labportalbackend.admin.audit.service.AuditLogService;
import com.web.labportalbackend.admin.systemconfig.entity.SystemConfigEntity;
import com.web.labportalbackend.admin.systemconfig.dto.SystemConfigRequest;
import com.web.labportalbackend.admin.systemconfig.repository.SystemConfigRepository;
import com.web.labportalbackend.admin.systemconfig.service.impl.SystemConfigServiceImpl;
import com.web.labportalbackend.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemConfigServiceImplTest {

    @Mock SystemConfigRepository systemConfigRepository;
    @Mock UserRepository userRepository;
    @Mock AuditLogService auditLogService;

    @Test
    void malformedPersistedConfigUsesSecureApprovedReportDefault() {
        SystemConfigEntity entity = new SystemConfigEntity();
        entity.setConfigKey("GLOBAL_SYSTEM_CONFIG");
        entity.setConfigValueJson("{malformed");
        when(systemConfigRepository.findByConfigKeyAndDeletedFalse("GLOBAL_SYSTEM_CONFIG"))
                .thenReturn(Optional.of(entity));
        SystemConfigServiceImpl service = new SystemConfigServiceImpl(
                systemConfigRepository, userRepository, new ObjectMapper(), auditLogService);

        var config = service.getConfig();

        assertTrue(config.research().requireApprovedReportBeforeTaskDone());
    }

    @Test
    void absentPersistedConfigUsesSecureApprovedReportDefault() {
        when(systemConfigRepository.findByConfigKeyAndDeletedFalse("GLOBAL_SYSTEM_CONFIG"))
                .thenReturn(Optional.empty());
        SystemConfigServiceImpl service = new SystemConfigServiceImpl(
                systemConfigRepository, userRepository, new ObjectMapper(), auditLogService);

        var config = service.getConfig();

        assertTrue(config.research().requireApprovedReportBeforeTaskDone());
    }

    @Test
    void nullOrIncompletePersistedConfigUsesSecureApprovedReportDefault() {
        SystemConfigEntity entity = new SystemConfigEntity();
        entity.setConfigKey("GLOBAL_SYSTEM_CONFIG");
        entity.setConfigValueJson("null");
        when(systemConfigRepository.findByConfigKeyAndDeletedFalse("GLOBAL_SYSTEM_CONFIG"))
                .thenReturn(Optional.of(entity));
        SystemConfigServiceImpl service = new SystemConfigServiceImpl(
                systemConfigRepository, userRepository, new ObjectMapper(), auditLogService);

        var config = service.getConfig();

        assertTrue(config.research().requireApprovedReportBeforeTaskDone());
    }

    @Test
    void persistedConfigWithMissingOrNullResearchSectionUsesSecureDefault() {
        SystemConfigEntity entity = new SystemConfigEntity();
        entity.setConfigKey("GLOBAL_SYSTEM_CONFIG");
        when(systemConfigRepository.findByConfigKeyAndDeletedFalse("GLOBAL_SYSTEM_CONFIG"))
                .thenReturn(Optional.of(entity));
        SystemConfigServiceImpl service = new SystemConfigServiceImpl(
                systemConfigRepository, userRepository, new ObjectMapper(), auditLogService);

        for (String malformedShape : new String[]{"{}", "{\"research\":null}"}) {
            entity.setConfigValueJson(malformedShape);

            var config = service.getConfig();

            assertTrue(config.research().requireApprovedReportBeforeTaskDone(), malformedShape);
        }
    }

    @Test
    void statusAuthorizationReadUsesDedicatedLockingRepositoryAndFailsClosedForUnusableJson() {
        SystemConfigEntity entity = new SystemConfigEntity();
        entity.setConfigKey("GLOBAL_SYSTEM_CONFIG");
        when(systemConfigRepository
                .findByConfigKeyForStatusAuthorization("GLOBAL_SYSTEM_CONFIG"))
                .thenReturn(Optional.of(entity));
        SystemConfigServiceImpl service = new SystemConfigServiceImpl(
                systemConfigRepository, userRepository, new ObjectMapper(), auditLogService);

        for (String unusableJson :
                new String[]{null, "{malformed", "null", "{}", "{\"research\":null}"}) {
            entity.setConfigValueJson(unusableJson);

            var config = service.getConfigForStatusAuthorization();

            assertTrue(
                    config.research().requireApprovedReportBeforeTaskDone(),
                    String.valueOf(unusableJson));
        }
        verify(systemConfigRepository, times(5))
                .findByConfigKeyForStatusAuthorization("GLOBAL_SYSTEM_CONFIG");
        verify(systemConfigRepository, never())
                .findByConfigKeyAndDeletedFalse("GLOBAL_SYSTEM_CONFIG");
    }

    @Test
    void legacyPersistedConfigKeepsExistingSectionsAndAddsOperationalDefaults() {
        SystemConfigEntity entity = new SystemConfigEntity();
        entity.setConfigKey("GLOBAL_SYSTEM_CONFIG");
        entity.setConfigValueJson("""
                {"account":{"requireEmailVerification":true,"defaultRegisterRole":"STUDENT","maxLoginAttempts":7},
                 "lab":{"oneManagerOneLab":true,"hideInactiveLabsFromStudent":true,"disableApplyForInactiveLab":true,"disableBookingForInactiveLab":true},
                 "booking":{"checkinWindowMinutes":15,"cancelBeforeMinutes":30,"hidePastSlots":true,"hideCancelledSlots":true},
                 "upload":{"reportMaxSizeMb":10,"productMaxSizeMb":50,"reportAllowedTypes":["pdf"],"productAllowedTypes":["zip"]},
                 "research":{"evaluationMaxScore":10,"requireApprovedReportBeforeTaskDone":true,"requireLeaderReviewBeforeManagerReview":true,"allowMemberPersonalProductUpload":true}}
                """);
        when(systemConfigRepository.findByConfigKeyAndDeletedFalse("GLOBAL_SYSTEM_CONFIG"))
                .thenReturn(Optional.of(entity));
        SystemConfigServiceImpl service = new SystemConfigServiceImpl(
                systemConfigRepository, userRepository, new ObjectMapper(), auditLogService);

        var config = service.getConfig();

        assertEquals(7, config.account().maxLoginAttempts());
        assertEquals(100, config.notification().maxPageSize());
        assertEquals(365, config.retention().auditLogDays());
    }

    @Test
    void rejectsUnknownSecretEditInsteadOfSilentlyPersistingIt() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        SystemConfigRequest request = objectMapper.readValue(
                validRequestJson().replace("\"maxContextTokens\":8192",
                        "\"maxContextTokens\":8192,\"apiSecret\":\"must-not-persist\""),
                SystemConfigRequest.class);
        SystemConfigServiceImpl service = new SystemConfigServiceImpl(
                systemConfigRepository, userRepository, objectMapper, auditLogService);

        assertThrows(IllegalArgumentException.class, () -> service.updateConfig(request));
        verify(systemConfigRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsInvalidThresholdQuotaAndRetentionValues() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        String invalid = validRequestJson()
                .replace("\"maxRequestsPerDay\":100", "\"maxRequestsPerDay\":0")
                .replace("\"confidenceThreshold\":0.8", "\"confidenceThreshold\":1.2")
                .replace("\"notificationDays\":90", "\"notificationDays\":0");
        SystemConfigRequest request = objectMapper.readValue(invalid, SystemConfigRequest.class);
        SystemConfigServiceImpl service = new SystemConfigServiceImpl(
                systemConfigRepository, userRepository, objectMapper, auditLogService);

        assertThrows(IllegalArgumentException.class, () -> service.updateConfig(request));
        verify(systemConfigRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private String validRequestJson() {
        return """
                {"account":{"requireEmailVerification":true,"defaultRegisterRole":"STUDENT","maxLoginAttempts":5},
                 "lab":{"oneManagerOneLab":true,"hideInactiveLabsFromStudent":true,"disableApplyForInactiveLab":true,"disableBookingForInactiveLab":true},
                 "booking":{"checkinWindowMinutes":10,"cancelBeforeMinutes":30,"hidePastSlots":true,"hideCancelledSlots":true},
                 "upload":{"reportMaxSizeMb":10,"productMaxSizeMb":50,"reportAllowedTypes":["pdf"],"productAllowedTypes":["zip"]},
                 "research":{"evaluationMaxScore":10,"requireApprovedReportBeforeTaskDone":true,"requireLeaderReviewBeforeManagerReview":true,"allowMemberPersonalProductUpload":true},
                 "ai":{"enabled":true,"maxRequestsPerDay":100,"maxContextTokens":8192},
                 "face":{"enabled":true,"confidenceThreshold":0.8,"livenessThreshold":0.8},
                 "qrFallback":{"enabled":true,"tokenTtlSeconds":300},
                 "notification":{"enabled":true,"maxPageSize":100},
                 "retention":{"notificationDays":90,"aiUsageLogDays":180,"faceCheckinLogDays":180,"auditLogDays":365},
                 "operational":{"maxPageSize":100,"includeFailureReasonCodes":true}}
                """;
    }
}
