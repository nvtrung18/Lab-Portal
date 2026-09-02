package com.web.labportalbackend.face.service;

import com.web.labportalbackend.booking.entity.Booking;
import com.web.labportalbackend.face.entity.FaceCheckinLogEntity;
import com.web.labportalbackend.face.entity.FaceSecurityConfigEntity;
import com.web.labportalbackend.face.enums.FaceCheckinMethod;
import com.web.labportalbackend.face.enums.FaceCheckinResult;
import com.web.labportalbackend.face.enums.FaceFallbackReason;
import com.web.labportalbackend.face.enums.FaceProfileStatus;
import com.web.labportalbackend.face.repository.FaceCheckinLogRepository;
import com.web.labportalbackend.face.repository.FaceProfileRepository;
import com.web.labportalbackend.face.repository.FaceSecurityConfigRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FaceFallbackPolicy {

    private final FaceSecurityConfigRepository securityConfigRepository;
    private final FaceProfileRepository profileRepository;
    private final FaceCheckinLogRepository checkinLogRepository;

    public void assertQrAllowed(Booking booking, FaceFallbackReason reason) {
        if (reason == null) {
            throw new IllegalArgumentException("QR fallback reason is required");
        }
        FaceSecurityConfigEntity config = activeConfig();
        boolean allowed = switch (reason) {
            case FACE_DISABLED -> !Boolean.TRUE.equals(config.getFaceEnabled())
                    && Boolean.TRUE.equals(config.getQrWhenFaceDisabled());
            case FACE_SERVICE_UNAVAILABLE -> Boolean.TRUE.equals(config.getQrWhenServiceUnavailable())
                    && hasServiceFailureEvidence(booking.getId());
            case FACE_PROFILE_UNAVAILABLE -> Boolean.TRUE.equals(config.getQrWhenProfileUnavailable())
                    && profileRepository.findByUserIdAndProfileStatusAndActiveTrueAndDeletedFalse(
                            booking.getUser().getId(), FaceProfileStatus.ACTIVE).isEmpty();
            case OTHER -> true;
        };
        if (!allowed) {
            throw new IllegalStateException("QR fallback is not allowed for the supplied reason");
        }
    }

    public void assertManualAllowed() {
        if (!Boolean.TRUE.equals(activeConfig().getManualOverrideEnabled())) {
            throw new IllegalStateException("Manual check-in override is disabled");
        }
    }

    private boolean hasServiceFailureEvidence(Long bookingId) {
        return checkinLogRepository.findFirstByBookingIdOrderByCreatedAtDescIdDesc(bookingId)
                .filter(log -> log.getCheckinMethod() == FaceCheckinMethod.FACE)
                .filter(log -> log.getResult() == FaceCheckinResult.FAILED)
                .map(FaceCheckinLogEntity::getFailureReason)
                .filter("SERVICE_ERROR"::equals)
                .isPresent();
    }

    private FaceSecurityConfigEntity activeConfig() {
        List<FaceSecurityConfigEntity> configs = securityConfigRepository.findAllByActiveTrueAndDeletedFalse();
        if (configs.size() != 1) {
            throw new IllegalStateException("Exactly one active face security configuration is required");
        }
        return configs.getFirst();
    }
}
