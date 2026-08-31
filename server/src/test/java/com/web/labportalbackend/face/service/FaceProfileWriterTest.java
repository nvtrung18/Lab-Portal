package com.web.labportalbackend.face.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.web.labportalbackend.admin.audit.service.AuditLogService;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.face.entity.FaceConsentLogEntity;
import com.web.labportalbackend.face.entity.FaceProfileEntity;
import com.web.labportalbackend.face.enums.FaceConsentStatus;
import com.web.labportalbackend.face.repository.FaceConsentLogRepository;
import com.web.labportalbackend.face.repository.FaceProfileRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class FaceProfileWriterTest {

    @Test
    void consentWithdrawalDisablesExistingProfileAndAuditsChange() {
        FaceConsentLogRepository consentRepository = mock(FaceConsentLogRepository.class);
        FaceProfileRepository profileRepository = mock(FaceProfileRepository.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        FaceProfileWriter writer = new FaceProfileWriter(consentRepository, profileRepository, auditLogService);
        User actor = mock(User.class);
        User target = mock(User.class);
        FaceProfileEntity profile = mock(FaceProfileEntity.class);
        when(target.getId()).thenReturn(9L);
        when(profileRepository.findByUserIdAndDeletedFalse(9L)).thenReturn(Optional.of(profile));
        FaceConsentLogEntity saved = FaceConsentLogEntity.builder().user(target).changedBy(actor)
                .consentStatus(FaceConsentStatus.WITHDRAWN).build();
        when(consentRepository.save(org.mockito.ArgumentMatchers.any())).thenReturn(saved);

        writer.changeConsent(actor, target, FaceConsentStatus.WITHDRAWN, "privacy choice");

        verify(profile).disable();
        verify(auditLogService).log(org.mockito.ArgumentMatchers.eq(actor),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("FACE_CONSENT"), org.mockito.ArgumentMatchers.eq(9L),
                org.mockito.ArgumentMatchers.contains("WITHDRAWN"));
    }
}
