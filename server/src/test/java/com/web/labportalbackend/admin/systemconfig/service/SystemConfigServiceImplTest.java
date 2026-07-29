package com.web.labportalbackend.admin.systemconfig.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.labportalbackend.admin.audit.service.AuditLogService;
import com.web.labportalbackend.admin.systemconfig.entity.SystemConfigEntity;
import com.web.labportalbackend.admin.systemconfig.repository.SystemConfigRepository;
import com.web.labportalbackend.admin.systemconfig.service.impl.SystemConfigServiceImpl;
import com.web.labportalbackend.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
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
}
