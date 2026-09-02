package com.web.labportalbackend.face.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.face.client.FaceEmbedResponse;
import com.web.labportalbackend.face.client.FaceGuidanceResult;
import com.web.labportalbackend.face.client.FaceMatchRequest;
import com.web.labportalbackend.face.client.FaceMatchResponse;
import com.web.labportalbackend.face.client.FaceProcessingClient;
import com.web.labportalbackend.face.client.FaceQualityResult;
import com.web.labportalbackend.face.dto.request.FaceRegistrationRequest;
import com.web.labportalbackend.face.dto.request.FaceGuidanceRequest;
import com.web.labportalbackend.face.dto.request.FaceChallengeFrameRequest;
import com.web.labportalbackend.face.entity.FaceConsentLogEntity;
import com.web.labportalbackend.face.entity.FaceProfileEntity;
import com.web.labportalbackend.face.entity.FaceSecurityConfigEntity;
import com.web.labportalbackend.face.enums.FaceConsentStatus;
import com.web.labportalbackend.face.enums.FaceProfileStatus;
import com.web.labportalbackend.face.repository.FaceConsentLogRepository;
import com.web.labportalbackend.face.repository.FaceProfileRepository;
import com.web.labportalbackend.face.repository.FaceSecurityConfigRepository;
import com.web.labportalbackend.face.security.FaceEmbeddingCipher;
import com.web.labportalbackend.face.service.impl.FaceProfileServiceImpl;
import com.web.labportalbackend.lab.repository.MembershipRepository;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class FaceProfileServiceImplTest {

    private static final String KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final FaceConsentLogRepository consentRepository = mock(FaceConsentLogRepository.class);
    private final FaceProfileRepository profileRepository = mock(FaceProfileRepository.class);
    private final FaceProcessingClient processingClient = mock(FaceProcessingClient.class);
    private final FaceProfileWriter writer = mock(FaceProfileWriter.class);
    private final MembershipRepository membershipRepository = mock(MembershipRepository.class);
    private final FaceSecurityConfigRepository securityConfigRepository = mock(FaceSecurityConfigRepository.class);
    private final FaceEmbeddingCipher cipher = new FaceEmbeddingCipher(KEY);
    private final User actor = mock(User.class);
    private FaceProfileServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FaceProfileServiceImpl(userRepository, consentRepository, profileRepository,
                processingClient, cipher, writer, new ObjectMapper(), membershipRepository,
                securityConfigRepository);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("student@example.test", "n/a", List.of()));
        when(userRepository.findByEmailOrUsername("student@example.test", "student@example.test"))
                .thenReturn(Optional.of(actor));
        when(actor.getId()).thenReturn(7L);
        when(actor.hasRole("STUDENT")).thenReturn(true);
        when(membershipRepository.existsByUserIdAndActiveTrueAndDeletedFalse(7L)).thenReturn(true);
        when(securityConfigRepository.findAllByActiveTrueAndDeletedFalse()).thenReturn(List.of(
                FaceSecurityConfigEntity.builder()
                        .configKey("test")
                        .faceEnabled(true)
                        .confidenceThreshold(new BigDecimal("0.8500"))
                        .livenessThreshold(new BigDecimal("0.7000"))
                        .livenessRequired(true)
                        .qrWhenFaceDisabled(true)
                        .qrWhenServiceUnavailable(true)
                        .qrWhenProfileUnavailable(true)
                        .manualOverrideEnabled(true)
                        .build()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listProfilesReturnsSafeMetadataInRepositoryOrder() {
        var profile = new com.web.labportalbackend.face.dto.response.FaceProfileResponse(
                7L, FaceProfileStatus.ACTIVE, "opencv-sface-2021dec",
                Instant.parse("2026-09-01T12:00:00Z"));
        when(profileRepository.findAllProfileMetadata()).thenReturn(List.of(profile));

        var result = service.listProfiles();

        assertEquals(1, result.size());
        assertEquals(7L, result.getFirst().userId());
        assertEquals(FaceProfileStatus.ACTIVE, result.getFirst().status());
        assertEquals("opencv-sface-2021dec", result.getFirst().embeddingModel());
    }

    @Test
    void registrationEncryptsEmbeddingBeforePersistence() {
        FaceConsentLogEntity consent = FaceConsentLogEntity.builder().consentStatus(FaceConsentStatus.GRANTED).build();
        when(consentRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(7L)).thenReturn(Optional.of(consent));
        when(processingClient.embed(any())).thenReturn(new FaceEmbedResponse(
                "OK", List.of(0.1, 0.2), "face-model-v1", new FaceQualityResult(true, null),
                0.98, 0.91, null));
        when(processingClient.match(any())).thenReturn(
                new FaceMatchResponse("MATCH", 0.93, 0.91, null));
        FaceProfileEntity saved = mock(FaceProfileEntity.class);
        when(saved.getUser()).thenReturn(actor);
        when(saved.getProfileStatus()).thenReturn(FaceProfileStatus.ACTIVE);
        when(saved.getEmbeddingModel()).thenReturn("face-model-v1-multiview-v1");
        when(saved.getUpdatedAt()).thenReturn(Instant.parse("2026-08-31T00:00:00Z"));
        when(writer.upsertProfile(eq(actor), eq(actor), any(), eq("face-model-v1-multiview-v1"))).thenReturn(saved);

        service.register(null, request());

        ArgumentCaptor<String> encrypted = ArgumentCaptor.forClass(String.class);
        verify(writer).upsertProfile(eq(actor), eq(actor), encrypted.capture(), eq("face-model-v1-multiview-v1"));
        assertNotEquals("[0.1,0.2]", encrypted.getValue());
        assertEquals("[[0.1,0.2],[0.1,0.2],[0.1,0.2]]", cipher.decrypt(encrypted.getValue()));
    }

    @Test
    void registrationUsesSFaceIdentityThresholdForSideViewsInsteadOfCheckinThreshold() {
        grantConsent();
        when(processingClient.embed(any())).thenReturn(new FaceEmbedResponse(
                "OK", List.of(0.1, 0.2), "face-model-v1", new FaceQualityResult(true, null),
                0.98, 0.91, null));
        when(processingClient.match(any())).thenReturn(
                new FaceMatchResponse("MATCH", 0.50, null, null));
        FaceProfileEntity saved = mock(FaceProfileEntity.class);
        when(saved.getUser()).thenReturn(actor);
        when(saved.getProfileStatus()).thenReturn(FaceProfileStatus.ACTIVE);
        when(saved.getEmbeddingModel()).thenReturn("face-model-v1-multiview-v1");
        when(writer.upsertProfile(eq(actor), eq(actor), any(), eq("face-model-v1-multiview-v1")))
                .thenReturn(saved);

        service.register(null, request());

        ArgumentCaptor<FaceMatchRequest> matchRequest = ArgumentCaptor.forClass(FaceMatchRequest.class);
        verify(processingClient, org.mockito.Mockito.times(2)).match(matchRequest.capture());
        assertEquals(0.363, matchRequest.getAllValues().getFirst().confidenceThreshold());
        verify(writer).upsertProfile(eq(actor), eq(actor), any(), eq("face-model-v1-multiview-v1"));
    }

    @Test
    void registrationWithoutGrantedConsentFailsBeforeCallingFaceService() {
        FaceConsentLogEntity consent = FaceConsentLogEntity.builder()
                .consentStatus(FaceConsentStatus.WITHDRAWN).build();
        when(consentRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(7L)).thenReturn(Optional.of(consent));

        assertThrows(IllegalStateException.class, () -> service.register(null, request()));

        verify(processingClient, never()).embed(any());
        verify(writer, never()).upsertProfile(any(), any(), any(), any());
    }

    @Test
    void registrationWithoutActiveLabMembershipFailsBeforeCallingFaceService() {
        when(membershipRepository.existsByUserIdAndActiveTrueAndDeletedFalse(7L)).thenReturn(false);

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.register(null, request()));

        verify(processingClient, never()).embed(any());
        verify(writer, never()).upsertProfile(any(), any(), any(), any());
    }

    @Test
    void registrationExplainsWhenNoFaceWasDetected() {
        grantConsent();
        when(processingClient.embed(any())).thenReturn(new FaceEmbedResponse(
                "NO_FACE", null, null, new FaceQualityResult(false, "NO_FACE"),
                null, null, "NO_FACE"));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.register(null, request()));

        assertEquals("Không phát hiện khuôn mặt. Hãy đưa toàn bộ khuôn mặt vào giữa khung hình và nhìn thẳng vào camera.",
                exception.getMessage());
        verify(writer, never()).upsertProfile(any(), any(), any(), any());
    }

    @Test
    void registrationExplainsHowToCorrectBlurredImage() {
        grantConsent();
        when(processingClient.embed(any())).thenReturn(new FaceEmbedResponse(
                "LOW_QUALITY", null, null, new FaceQualityResult(false, "BLURRY"),
                0.95, null, "LOW_QUALITY"));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.register(null, request()));

        assertEquals("Ảnh khuôn mặt bị mờ. Hãy giữ yên đầu, lau sạch camera và chụp lại.",
                exception.getMessage());
    }

    @Test
    void registrationExplainsHowToRetryFailedLiveness() {
        grantConsent();
        when(processingClient.embed(any())).thenReturn(new FaceEmbedResponse(
                "SPOOF_DETECTED", null, null, new FaceQualityResult(true, null),
                0.95, 0.2, "SPOOF_DETECTED"));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.register(null, request()));

        assertEquals("Không xác minh được khuôn mặt thật (điểm liveness 0.2000). Hãy dùng camera trực tiếp, nhìn thẳng và không dùng ảnh chụp hoặc màn hình khác.",
                exception.getMessage());
    }

    @Test
    void registrationExplainsWhenRequestedHeadTurnWasNotDetected() {
        grantConsent();
        when(processingClient.embed(any())).thenReturn(new FaceEmbedResponse(
                "SPOOF_DETECTED", null, null, new FaceQualityResult(true, null),
                0.95, 0.0003, false, "CHALLENGE_TURN_NOT_DETECTED"));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.register(null, request()));

        assertEquals("Chưa phát hiện đủ chuyển động quay đầu. Khi hiện 'BÂY GIỜ', hãy quay sang đúng hướng trên màn hình và giữ tư thế đến khi camera tự tắt.",
                exception.getMessage());
    }

    @Test
    void guidanceReturnsLiveChecksWithoutPersistingProfile() {
        grantConsent();
        when(processingClient.guidance(any())).thenReturn(new FaceGuidanceResult(
                1, true, true, true, true, true, true,
                0.5, 0.48, 0.35, 0.55, null));

        var result = service.guidance(null, new FaceGuidanceRequest(
                Base64.getEncoder().encodeToString("image".getBytes()), "image/jpeg"));

        assertEquals(1, result.detectedFaces());
        assertEquals(true, result.singleFace());
        assertEquals(true, result.faceInGuide());
        verify(processingClient).guidance(any());
        verify(writer, never()).upsertProfile(any(), any(), any(), any());
    }

    private void grantConsent() {
        FaceConsentLogEntity consent = FaceConsentLogEntity.builder()
                .consentStatus(FaceConsentStatus.GRANTED).build();
        when(consentRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(7L)).thenReturn(Optional.of(consent));
    }

    private FaceRegistrationRequest request() {
        String image = Base64.getEncoder().encodeToString("image".getBytes());
        var frame = new FaceChallengeFrameRequest(image, "image/jpeg");
        return new FaceRegistrationRequest(image, "image/jpeg", true,
                List.of(frame, frame), List.of(frame, frame, frame), "signed-challenge");
    }
}
