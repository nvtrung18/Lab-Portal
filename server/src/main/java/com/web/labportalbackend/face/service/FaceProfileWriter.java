package com.web.labportalbackend.face.service;

import com.web.labportalbackend.admin.audit.enums.AuditAction;
import com.web.labportalbackend.admin.audit.enums.AuditModule;
import com.web.labportalbackend.admin.audit.service.AuditLogService;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.face.entity.FaceConsentLogEntity;
import com.web.labportalbackend.face.entity.FaceProfileEntity;
import com.web.labportalbackend.face.enums.FaceConsentStatus;
import com.web.labportalbackend.face.repository.FaceConsentLogRepository;
import com.web.labportalbackend.face.repository.FaceProfileRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class FaceProfileWriter {

    private final FaceConsentLogRepository consentLogRepository;
    private final FaceProfileRepository profileRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public FaceConsentLogEntity changeConsent(User actor, User target, FaceConsentStatus status, String reason) {
        FaceConsentLogEntity consent = consentLogRepository.save(FaceConsentLogEntity.builder()
                .user(target).changedBy(actor).consentStatus(status).reason(normalize(reason)).build());
        if (status != FaceConsentStatus.GRANTED) {
            profileRepository.findByUserIdAndDeletedFalse(target.getId()).ifPresent(FaceProfileEntity::disable);
        }
        auditLogService.log(actor, AuditAction.FACE_CONSENT_CHANGED, AuditModule.FACE,
                "FACE_CONSENT", target.getId(), "Face consent state changed to " + status.name());
        return consent;
    }

    @Transactional
    public FaceProfileEntity upsertProfile(
            User actor,
            User target,
            String encryptedEmbedding,
            String embeddingModel
    ) {
        FaceConsentStatus consent = currentConsent(target.getId());
        FaceProfileEntity profile = profileRepository.findByUserId(target.getId())
                .orElseGet(() -> FaceProfileEntity.builder().user(target).build());
        profile.replaceEmbedding(encryptedEmbedding, embeddingModel, consent);
        FaceProfileEntity saved = profileRepository.save(profile);
        auditLogService.log(actor, AuditAction.FACE_PROFILE_CHANGED, AuditModule.FACE,
                "FACE_PROFILE", saved.getId(), "Face profile registered or updated");
        return saved;
    }

    @Transactional
    public void deleteProfile(User actor, User target, String encryptedTombstone) {
        FaceProfileEntity profile = profileRepository.findByUserIdAndDeletedFalse(target.getId())
                .orElseThrow(() -> new EntityNotFoundException("Face profile not found"));
        profile.markDeleted(encryptedTombstone);
        consentLogRepository.save(FaceConsentLogEntity.builder().user(target).changedBy(actor)
                .consentStatus(FaceConsentStatus.DELETED).reason("Face profile deleted").build());
        auditLogService.log(actor, AuditAction.FACE_PROFILE_CHANGED, AuditModule.FACE,
                "FACE_PROFILE", profile.getId(), "Face profile deleted and embedding invalidated");
    }

    private FaceConsentStatus currentConsent(Long userId) {
        return consentLogRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(userId)
                .map(FaceConsentLogEntity::getConsentStatus)
                .orElseThrow(() -> new IllegalStateException("Granted face consent is required"));
    }

    private String normalize(String reason) {
        return reason == null || reason.isBlank() ? null : reason.trim();
    }
}
