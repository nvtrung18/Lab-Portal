package com.web.labportalbackend.face.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.face.client.FaceEmbedRequest;
import com.web.labportalbackend.face.client.FaceEmbedResponse;
import com.web.labportalbackend.face.client.FaceProcessingClient;
import com.web.labportalbackend.face.dto.request.FaceConsentRequest;
import com.web.labportalbackend.face.dto.request.FaceRegistrationRequest;
import com.web.labportalbackend.face.dto.response.FaceConsentResponse;
import com.web.labportalbackend.face.dto.response.FaceProfileResponse;
import com.web.labportalbackend.face.entity.FaceConsentLogEntity;
import com.web.labportalbackend.face.entity.FaceProfileEntity;
import com.web.labportalbackend.face.enums.FaceConsentStatus;
import com.web.labportalbackend.face.repository.FaceConsentLogRepository;
import com.web.labportalbackend.face.repository.FaceProfileRepository;
import com.web.labportalbackend.face.security.FaceEmbeddingCipher;
import com.web.labportalbackend.face.service.FaceProfileService;
import com.web.labportalbackend.face.service.FaceProfileWriter;
import jakarta.persistence.EntityNotFoundException;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FaceProfileServiceImpl implements FaceProfileService {

    private final UserRepository userRepository;
    private final FaceConsentLogRepository consentLogRepository;
    private final FaceProfileRepository profileRepository;
    private final FaceProcessingClient processingClient;
    private final FaceEmbeddingCipher embeddingCipher;
    private final FaceProfileWriter writer;
    private final ObjectMapper objectMapper;

    @Override
    public FaceConsentResponse changeConsent(Long targetUserId, FaceConsentRequest request) {
        if (request.status() != FaceConsentStatus.GRANTED && request.status() != FaceConsentStatus.WITHDRAWN) {
            throw new IllegalArgumentException("Consent can only be granted or withdrawn through this API");
        }
        User actor = currentUser();
        User target = authorizedTarget(actor, targetUserId);
        FaceConsentLogEntity saved = writer.changeConsent(actor, target, request.status(), request.reason());
        return consentResponse(saved);
    }

    @Override
    public FaceConsentResponse getConsent(Long targetUserId) {
        User target = authorizedTarget(currentUser(), targetUserId);
        return consentLogRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(target.getId())
                .map(this::consentResponse)
                .orElseThrow(() -> new EntityNotFoundException("Face consent not found"));
    }

    @Override
    public FaceProfileResponse register(Long targetUserId, FaceRegistrationRequest request) {
        User actor = currentUser();
        User target = authorizedTarget(actor, targetUserId);
        requireGrantedConsent(target.getId());
        validateImage(request.imageBase64());

        FaceEmbedResponse result = processingClient.embed(new FaceEmbedRequest(
                request.imageBase64(), request.contentType(), request.livenessRequired()));
        validateEmbeddingResult(result, request.livenessRequired());
        String encrypted = embeddingCipher.encrypt(serializeEmbedding(result.embedding()));
        return profileResponse(writer.upsertProfile(actor, target, encrypted, result.embeddingModel()));
    }

    @Override
    public FaceProfileResponse getProfile(Long targetUserId) {
        User target = authorizedTarget(currentUser(), targetUserId);
        return profileResponse(profileRepository.findByUserIdAndDeletedFalse(target.getId())
                .orElseThrow(() -> new EntityNotFoundException("Face profile not found")));
    }

    @Override
    public void deleteProfile(Long targetUserId) {
        User actor = currentUser();
        User target = authorizedTarget(actor, targetUserId);
        writer.deleteProfile(actor, target, embeddingCipher.encrypt("[]"));
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication is required");
        }
        return userRepository.findByEmailOrUsername(authentication.getName(), authentication.getName())
                .orElseThrow(() -> new AccessDeniedException("Authenticated user not found"));
    }

    private User authorizedTarget(User actor, Long targetUserId) {
        Long resolvedId = targetUserId == null ? actor.getId() : targetUserId;
        if (!actor.getId().equals(resolvedId) && !actor.hasRole("ADMIN")) {
            throw new AccessDeniedException("Face profile access is owner or Admin only");
        }
        if (actor.getId().equals(resolvedId)) {
            if (!actor.hasRole("STUDENT") && !actor.hasRole("ADMIN")) {
                throw new AccessDeniedException("Only students may own a face profile");
            }
            return actor;
        }
        return userRepository.findById(resolvedId)
                .filter(user -> !Boolean.TRUE.equals(user.getDeleted()))
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
    }

    private void requireGrantedConsent(Long userId) {
        FaceConsentStatus current = consentLogRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(userId)
                .map(FaceConsentLogEntity::getConsentStatus)
                .orElseThrow(() -> new IllegalStateException("Granted face consent is required"));
        if (!current.allowsActiveProfile()) {
            throw new IllegalStateException("Granted face consent is required");
        }
    }

    private void validateEmbeddingResult(FaceEmbedResponse result, boolean livenessRequired) {
        if (result == null || !"OK".equals(result.result()) || result.embedding() == null
                || result.embedding().isEmpty() || result.embeddingModel() == null
                || result.embeddingModel().isBlank() || result.quality() == null || !result.quality().passed()) {
            throw new IllegalStateException("Face registration did not produce an acceptable embedding");
        }
        if (result.embedding().stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
            throw new IllegalStateException("Face service returned an invalid embedding");
        }
        if (livenessRequired && result.livenessScore() == null) {
            throw new IllegalStateException("Face service did not return the required liveness score");
        }
        validateScore(result.confidenceScore());
        validateScore(result.livenessScore());
        if (result.embedding().size() > 4096) {
            throw new IllegalStateException("Face service returned an oversized embedding");
        }
    }

    private void validateImage(String imageBase64) {
        try {
            byte[] decoded = Base64.getDecoder().decode(imageBase64);
            if (decoded.length == 0 || decoded.length > 10_000_000) {
                throw new IllegalArgumentException("Face image size is invalid");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Face image must be valid base64", exception);
        }
    }

    private void validateScore(Double score) {
        if (score != null && (!Double.isFinite(score) || score < 0 || score > 1)) {
            throw new IllegalStateException("Face service returned an invalid score");
        }
    }

    private String serializeEmbedding(List<Double> embedding) {
        try {
            return objectMapper.writeValueAsString(embedding);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Face embedding could not be serialized", exception);
        }
    }

    private FaceConsentResponse consentResponse(FaceConsentLogEntity consent) {
        return new FaceConsentResponse(consent.getUser().getId(), consent.getConsentStatus(), consent.getCreatedAt());
    }

    private FaceProfileResponse profileResponse(FaceProfileEntity profile) {
        return new FaceProfileResponse(profile.getUser().getId(), profile.getProfileStatus(),
                profile.getEmbeddingModel(), profile.getUpdatedAt());
    }
}
