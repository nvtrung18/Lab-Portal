package com.web.labportalbackend.face.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.web.labportalbackend.auth.entity.User;
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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FaceFallbackPolicyTest {

    private final FaceSecurityConfigRepository configRepository = mock(FaceSecurityConfigRepository.class);
    private final FaceProfileRepository profileRepository = mock(FaceProfileRepository.class);
    private final FaceCheckinLogRepository logRepository = mock(FaceCheckinLogRepository.class);
    private final Booking booking = mock(Booking.class);
    private final User student = mock(User.class);
    private FaceFallbackPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new FaceFallbackPolicy(configRepository, profileRepository, logRepository);
        when(booking.getId()).thenReturn(11L);
        when(booking.getUser()).thenReturn(student);
        when(student.getId()).thenReturn(7L);
    }

    @Test
    void faceDisabledRequiresBothDisabledStateAndEnabledPolicy() {
        when(configRepository.findAllByActiveTrueAndDeletedFalse()).thenReturn(List.of(config(false, true, true, true, true)));

        assertDoesNotThrow(() -> policy.assertQrAllowed(booking, FaceFallbackReason.FACE_DISABLED));
    }

    @Test
    void faceDisabledReasonIsDeniedWhileFaceIsEnabled() {
        when(configRepository.findAllByActiveTrueAndDeletedFalse()).thenReturn(List.of(config(true, true, true, true, true)));

        assertThrows(IllegalStateException.class,
                () -> policy.assertQrAllowed(booking, FaceFallbackReason.FACE_DISABLED));
    }

    @Test
    void serviceUnavailableRequiresPersistedServiceFailureEvidence() {
        when(configRepository.findAllByActiveTrueAndDeletedFalse()).thenReturn(List.of(config(true, true, true, true, true)));
        when(logRepository.findFirstByBookingIdOrderByCreatedAtDescIdDesc(11L)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> policy.assertQrAllowed(booking, FaceFallbackReason.FACE_SERVICE_UNAVAILABLE));

        when(logRepository.findFirstByBookingIdOrderByCreatedAtDescIdDesc(11L)).thenReturn(Optional.of(
                FaceCheckinLogEntity.builder().checkinMethod(FaceCheckinMethod.FACE)
                        .result(FaceCheckinResult.FAILED).failureReason("SERVICE_ERROR").build()));
        assertDoesNotThrow(() -> policy.assertQrAllowed(booking, FaceFallbackReason.FACE_SERVICE_UNAVAILABLE));
    }

    @Test
    void unavailableProfileReasonIsDeniedWhenActiveProfileExists() {
        when(configRepository.findAllByActiveTrueAndDeletedFalse()).thenReturn(List.of(config(true, true, true, true, true)));
        when(profileRepository.findByUserIdAndProfileStatusAndActiveTrueAndDeletedFalse(7L, FaceProfileStatus.ACTIVE))
                .thenReturn(Optional.of(mock(com.web.labportalbackend.face.entity.FaceProfileEntity.class)));

        assertThrows(IllegalStateException.class,
                () -> policy.assertQrAllowed(booking, FaceFallbackReason.FACE_PROFILE_UNAVAILABLE));
    }

    @Test
    void manualOverrideMustBeExplicitlyEnabled() {
        when(configRepository.findAllByActiveTrueAndDeletedFalse()).thenReturn(List.of(config(true, true, true, true, false)));
        assertThrows(IllegalStateException.class, policy::assertManualAllowed);

        when(configRepository.findAllByActiveTrueAndDeletedFalse()).thenReturn(List.of(config(true, true, true, true, true)));
        assertDoesNotThrow(policy::assertManualAllowed);
    }

    @Test
    void ambiguousActiveConfigurationFailsClosed() {
        when(configRepository.findAllByActiveTrueAndDeletedFalse()).thenReturn(List.of());

        assertThrows(IllegalStateException.class,
                () -> policy.assertQrAllowed(booking, FaceFallbackReason.FACE_DISABLED));
    }

    private FaceSecurityConfigEntity config(
            boolean enabled,
            boolean qrDisabled,
            boolean qrService,
            boolean qrProfile,
            boolean manual
    ) {
        return FaceSecurityConfigEntity.builder()
                .faceEnabled(enabled)
                .qrWhenFaceDisabled(qrDisabled)
                .qrWhenServiceUnavailable(qrService)
                .qrWhenProfileUnavailable(qrProfile)
                .manualOverrideEnabled(manual)
                .build();
    }
}
