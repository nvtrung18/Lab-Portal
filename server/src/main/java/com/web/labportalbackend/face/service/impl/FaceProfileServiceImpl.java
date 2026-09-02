package com.web.labportalbackend.face.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.face.client.FaceEmbedRequest;
import com.web.labportalbackend.face.client.FaceEmbedResponse;
import com.web.labportalbackend.face.client.FaceGuidanceImageRequest;
import com.web.labportalbackend.face.client.FaceGuidanceResult;
import com.web.labportalbackend.face.client.FaceChallengeFrame;
import com.web.labportalbackend.face.client.FaceMatchRequest;
import com.web.labportalbackend.face.client.FaceMatchResponse;
import com.web.labportalbackend.face.client.FaceProcessingClient;
import com.web.labportalbackend.face.dto.request.FaceConsentRequest;
import com.web.labportalbackend.face.dto.request.FaceGuidanceRequest;
import com.web.labportalbackend.face.dto.request.FaceRegistrationRequest;
import com.web.labportalbackend.face.dto.response.FaceConsentResponse;
import com.web.labportalbackend.face.dto.response.FaceChallengeResponse;
import com.web.labportalbackend.face.dto.response.FaceGuidanceResponse;
import com.web.labportalbackend.face.dto.response.FaceProfileResponse;
import com.web.labportalbackend.face.entity.FaceConsentLogEntity;
import com.web.labportalbackend.face.entity.FaceProfileEntity;
import com.web.labportalbackend.face.entity.FaceSecurityConfigEntity;
import com.web.labportalbackend.face.enums.FaceConsentStatus;
import com.web.labportalbackend.face.repository.FaceConsentLogRepository;
import com.web.labportalbackend.face.repository.FaceProfileRepository;
import com.web.labportalbackend.face.repository.FaceSecurityConfigRepository;
import com.web.labportalbackend.face.security.FaceEmbeddingCipher;
import com.web.labportalbackend.face.service.FaceProfileService;
import com.web.labportalbackend.face.service.FaceProfileWriter;
import com.web.labportalbackend.lab.repository.MembershipRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class FaceProfileServiceImpl implements FaceProfileService {

    // OpenCV SFace's documented cosine threshold for same-identity matching.
    // Enrollment uses this only to validate that side samples belong to the frontal identity;
    // the stricter configured confidence threshold remains unchanged for check-in.
    private static final double MULTIVIEW_IDENTITY_THRESHOLD = 0.363;

    private final UserRepository userRepository;
    private final FaceConsentLogRepository consentLogRepository;
    private final FaceProfileRepository profileRepository;
    private final FaceProcessingClient processingClient;
    private final FaceEmbeddingCipher embeddingCipher;
    private final FaceProfileWriter writer;
    private final ObjectMapper objectMapper;
    private final MembershipRepository membershipRepository;
    private final FaceSecurityConfigRepository securityConfigRepository;

    @Override
    public List<FaceProfileResponse> listProfiles() {
        return profileRepository.findAllProfileMetadata();
    }

    @Override
    public FaceConsentResponse changeConsent(Long targetUserId, FaceConsentRequest request) {
        if (request.status() != FaceConsentStatus.GRANTED && request.status() != FaceConsentStatus.WITHDRAWN) {
            throw new IllegalArgumentException("Consent can only be granted or withdrawn through this API");
        }
        User actor = currentUser();
        User target = authorizedTarget(actor, targetUserId);
        if (request.status() == FaceConsentStatus.GRANTED) {
            requireActiveLabMembership(target.getId());
        }
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
        requireActiveLabMembership(target.getId());
        requireGrantedConsent(target.getId());
        validateImage(request.imageBase64());
        request.sideImages().forEach(sideImage -> validateImage(sideImage.imageBase64()));

        FaceEmbedResponse front = processingClient.embed(new FaceEmbedRequest(
                request.imageBase64(), request.contentType(), request.livenessRequired(),
                request.challengeFrames() == null ? List.of() : request.challengeFrames().stream()
                        .map(frame -> new FaceChallengeFrame(frame.imageBase64(), frame.contentType())).toList(),
                request.challengeToken()));
        validateEmbeddingResult(front, request.livenessRequired());

        FaceSecurityConfigEntity securityConfig = securityConfig();
        List<List<Double>> embeddings = new java.util.ArrayList<>();
        embeddings.add(front.embedding());
        for (var sideImage : request.sideImages()) {
            FaceEmbedResponse side = processingClient.embed(new FaceEmbedRequest(
                    sideImage.imageBase64(), sideImage.contentType(), false));
            validateEmbeddingResult(side, false);
            if (!front.embeddingModel().equals(side.embeddingModel())) {
                throw new IllegalStateException("Face service returned inconsistent embedding models");
            }
            FaceMatchResponse identity = processingClient.match(new FaceMatchRequest(
                    sideImage.imageBase64(), sideImage.contentType(), List.of(front.embedding()),
                    MULTIVIEW_IDENTITY_THRESHOLD,
                    securityConfig.getLivenessThreshold().doubleValue(), false, List.of(), null));
            if (identity == null || !"MATCH".equals(identity.result())
                    || identity.confidenceScore() == null
                    || identity.confidenceScore() < MULTIVIEW_IDENTITY_THRESHOLD) {
                log.warn("Face multiview enrollment identity mismatch: score={}, threshold={}",
                        identity == null ? null : identity.confidenceScore(), MULTIVIEW_IDENTITY_THRESHOLD);
                throw new IllegalStateException(
                        "Mẫu góc trái hoặc phải không khớp với khuôn mặt chính diện. Hãy đăng ký lại.");
            }
            embeddings.add(side.embedding());
        }
        String encrypted = embeddingCipher.encrypt(serializeEmbeddings(embeddings));
        return profileResponse(writer.upsertProfile(actor, target, encrypted,
                front.embeddingModel() + "-multiview-v1"));
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

    @Override
    public FaceGuidanceResponse guidance(Long targetUserId, FaceGuidanceRequest request) {
        User target = authorizedTarget(currentUser(), targetUserId);
        requireActiveLabMembership(target.getId());
        requireGrantedConsent(target.getId());
        validateImage(request.imageBase64());
        FaceGuidanceResult result = processingClient.guidance(new FaceGuidanceImageRequest(
                request.imageBase64(), request.contentType(), false));
        validateGuidanceResult(result);
        return new FaceGuidanceResponse(
                result.detectedFaces(), result.singleFace(), result.faceInGuide(),
                result.facingForward(), result.landmarksVisible(), result.lightingGood(),
                result.sharpnessGood(), result.centerX(), result.centerY(),
                result.faceWidthRatio(), result.faceHeightRatio(), result.failureReason());
    }

    @Override
    public FaceChallengeResponse startChallenge(Long targetUserId) {
        User target = authorizedTarget(currentUser(), targetUserId);
        requireActiveLabMembership(target.getId());
        requireGrantedConsent(target.getId());
        var challenge = processingClient.startChallenge();
        return new FaceChallengeResponse(challenge.challengeToken(), challenge.action(), challenge.expiresAt());
    }

    private void requireActiveLabMembership(Long userId) {
        if (!membershipRepository.existsByUserIdAndActiveTrueAndDeletedFalse(userId)) {
            throw new AccessDeniedException("An active laboratory membership is required for face registration");
        }
    }

    private void validateEmbeddingResult(FaceEmbedResponse result, boolean livenessRequired) {
        if (result == null) {
            throw new IllegalStateException("Không nhận được kết quả xử lý khuôn mặt. Vui lòng thử lại.");
        }
        if (!"OK".equals(result.result()) || result.quality() == null || !result.quality().passed()) {
            throw new IllegalStateException(registrationFailureMessage(result));
        }
        if (result.embedding() == null || result.embedding().isEmpty()
                || result.embeddingModel() == null || result.embeddingModel().isBlank()) {
            throw new IllegalStateException("Dịch vụ khuôn mặt không trả về mẫu nhận diện hợp lệ. Vui lòng thử lại.");
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

    private void validateGuidanceResult(FaceGuidanceResult result) {
        if (result == null || result.detectedFaces() < 0) {
            throw new IllegalStateException("Dịch vụ khuôn mặt không trả về kết quả hướng dẫn hợp lệ.");
        }
        validateNormalizedValue(result.centerX());
        validateNormalizedValue(result.centerY());
        validateNormalizedValue(result.faceWidthRatio());
        validateNormalizedValue(result.faceHeightRatio());
        if (result.singleFace() && (result.centerX() == null || result.centerY() == null
                || result.faceWidthRatio() == null || result.faceHeightRatio() == null)) {
            throw new IllegalStateException("Dịch vụ khuôn mặt không trả về vị trí khuôn mặt hợp lệ.");
        }
    }

    private void validateNormalizedValue(Double value) {
        if (value != null && (!Double.isFinite(value) || value < 0 || value > 1)) {
            throw new IllegalStateException("Dịch vụ khuôn mặt trả về giá trị hướng dẫn không hợp lệ.");
        }
    }

    private String registrationFailureMessage(FaceEmbedResponse result) {
        String failureReason = result.failureReason();
        if ((failureReason == null || failureReason.isBlank() || "LOW_QUALITY".equals(failureReason))
                && result.quality() != null && result.quality().reason() != null
                && !result.quality().reason().isBlank()) {
            failureReason = result.quality().reason();
        }
        if (failureReason == null || failureReason.isBlank()) {
            failureReason = result.result();
        }
        return switch (failureReason) {
            case "NO_FACE" -> "Không phát hiện khuôn mặt. Hãy đưa toàn bộ khuôn mặt vào giữa khung hình và nhìn thẳng vào camera.";
            case "MULTIPLE_FACES" -> "Phát hiện nhiều khuôn mặt. Hãy đảm bảo chỉ có một người trong khung hình.";
            case "FACE_TOO_SMALL" -> "Khuôn mặt ở quá xa camera. Hãy tiến lại gần để khuôn mặt chiếm phần lớn khung hướng dẫn.";
            case "TOO_DARK" -> "Khuôn mặt đang quá tối. Hãy tăng ánh sáng phía trước và tránh đứng ngược sáng.";
            case "TOO_BRIGHT" -> "Khuôn mặt đang quá sáng. Hãy giảm ánh sáng trực tiếp hoặc tránh nguồn sáng mạnh.";
            case "BLURRY" -> "Ảnh khuôn mặt bị mờ. Hãy giữ yên đầu, lau sạch camera và chụp lại.";
            case "SPOOF_DETECTED" -> String.format(Locale.ROOT,
                    "Không xác minh được khuôn mặt thật (điểm liveness %.4f). Hãy dùng camera trực tiếp, nhìn thẳng và không dùng ảnh chụp hoặc màn hình khác.",
                    result.livenessScore() == null ? 0.0 : result.livenessScore());
            case "CHALLENGE_MISSING" -> "Thiếu chuỗi ảnh xác minh từ camera. Hãy mở lại camera và thực hiện đến khi camera tự tắt.";
            case "CHALLENGE_INVALID" -> "Phiên xác minh khuôn mặt đã hết hạn hoặc không hợp lệ. Hãy mở lại camera để thực hiện phiên mới.";
            case "CHALLENGE_FACE_INVALID" -> "Camera bị mất khuôn mặt trong khi xác minh. Hãy quay đầu vừa phải và luôn giữ toàn bộ khuôn mặt trong khung.";
            case "CHALLENGE_START_NOT_FRONTAL" -> "Khi bắt đầu xác minh, hãy nhìn thẳng vào camera rồi mới quay đầu theo hướng dẫn.";
            case "CHALLENGE_TURN_NOT_DETECTED" -> "Chưa phát hiện đủ chuyển động quay đầu. Khi hiện 'BÂY GIỜ', hãy quay sang đúng hướng trên màn hình và giữ tư thế đến khi camera tự tắt.";
            case "SERVICE_ERROR" -> "Dịch vụ khuôn mặt không thể xử lý ảnh lúc này. Vui lòng thử lại sau.";
            default -> "Ảnh khuôn mặt chưa đạt yêu cầu. Hãy chụp chính diện, đủ sáng, rõ nét và không che khuôn mặt.";
        };
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

    private String serializeEmbeddings(List<List<Double>> embeddings) {
        try {
            return objectMapper.writeValueAsString(embeddings);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Face embedding could not be serialized", exception);
        }
    }

    private FaceSecurityConfigEntity securityConfig() {
        List<FaceSecurityConfigEntity> configs = securityConfigRepository.findAllByActiveTrueAndDeletedFalse();
        if (configs.size() != 1) {
            throw new IllegalStateException("Exactly one active face security configuration is required");
        }
        return configs.getFirst();
    }

    private FaceConsentResponse consentResponse(FaceConsentLogEntity consent) {
        return new FaceConsentResponse(consent.getUser().getId(), consent.getConsentStatus(), consent.getCreatedAt());
    }

    private FaceProfileResponse profileResponse(FaceProfileEntity profile) {
        return new FaceProfileResponse(profile.getUser().getId(), profile.getProfileStatus(),
                profile.getEmbeddingModel(), profile.getUpdatedAt());
    }
}
