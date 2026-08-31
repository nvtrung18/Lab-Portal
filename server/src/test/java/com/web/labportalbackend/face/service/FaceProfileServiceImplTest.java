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
import com.web.labportalbackend.face.client.FaceProcessingClient;
import com.web.labportalbackend.face.client.FaceQualityResult;
import com.web.labportalbackend.face.dto.request.FaceRegistrationRequest;
import com.web.labportalbackend.face.entity.FaceConsentLogEntity;
import com.web.labportalbackend.face.entity.FaceProfileEntity;
import com.web.labportalbackend.face.enums.FaceConsentStatus;
import com.web.labportalbackend.face.enums.FaceProfileStatus;
import com.web.labportalbackend.face.repository.FaceConsentLogRepository;
import com.web.labportalbackend.face.repository.FaceProfileRepository;
import com.web.labportalbackend.face.security.FaceEmbeddingCipher;
import com.web.labportalbackend.face.service.impl.FaceProfileServiceImpl;
import java.time.Instant;
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
    private final FaceEmbeddingCipher cipher = new FaceEmbeddingCipher(KEY);
    private final User actor = mock(User.class);
    private FaceProfileServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FaceProfileServiceImpl(userRepository, consentRepository, profileRepository,
                processingClient, cipher, writer, new ObjectMapper());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("student@example.test", "n/a", List.of()));
        when(userRepository.findByEmailOrUsername("student@example.test", "student@example.test"))
                .thenReturn(Optional.of(actor));
        when(actor.getId()).thenReturn(7L);
        when(actor.hasRole("STUDENT")).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registrationEncryptsEmbeddingBeforePersistence() {
        FaceConsentLogEntity consent = FaceConsentLogEntity.builder().consentStatus(FaceConsentStatus.GRANTED).build();
        when(consentRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(7L)).thenReturn(Optional.of(consent));
        when(processingClient.embed(any())).thenReturn(new FaceEmbedResponse(
                "OK", List.of(0.1, 0.2), "face-model-v1", new FaceQualityResult(true, null),
                0.98, 0.91, null));
        FaceProfileEntity saved = mock(FaceProfileEntity.class);
        when(saved.getUser()).thenReturn(actor);
        when(saved.getProfileStatus()).thenReturn(FaceProfileStatus.ACTIVE);
        when(saved.getEmbeddingModel()).thenReturn("face-model-v1");
        when(saved.getUpdatedAt()).thenReturn(Instant.parse("2026-08-31T00:00:00Z"));
        when(writer.upsertProfile(eq(actor), eq(actor), any(), eq("face-model-v1"))).thenReturn(saved);

        service.register(null, request());

        ArgumentCaptor<String> encrypted = ArgumentCaptor.forClass(String.class);
        verify(writer).upsertProfile(eq(actor), eq(actor), encrypted.capture(), eq("face-model-v1"));
        assertNotEquals("[0.1,0.2]", encrypted.getValue());
        assertEquals("[0.1,0.2]", cipher.decrypt(encrypted.getValue()));
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

    private FaceRegistrationRequest request() {
        return new FaceRegistrationRequest(
                Base64.getEncoder().encodeToString("image".getBytes()), "image/jpeg", true);
    }
}
