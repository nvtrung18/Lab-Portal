package com.web.labportalbackend.face.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.web.labportalbackend.admin.systemconfig.dto.SystemConfigResponse;
import com.web.labportalbackend.admin.systemconfig.service.SystemConfigService;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.booking.entity.Booking;
import com.web.labportalbackend.booking.repository.BookingRepository;
import com.web.labportalbackend.common.enums.BookingStatus;
import com.web.labportalbackend.common.enums.TimeSlotStatus;
import com.web.labportalbackend.face.client.FaceMatchRequest;
import com.web.labportalbackend.face.client.FaceMatchResponse;
import com.web.labportalbackend.face.client.FaceProcessingClient;
import com.web.labportalbackend.face.client.FaceServiceException;
import com.web.labportalbackend.face.dto.request.FaceCheckinRequest;
import com.web.labportalbackend.face.dto.response.FaceCheckinResponse;
import com.web.labportalbackend.face.entity.FaceConsentLogEntity;
import com.web.labportalbackend.face.entity.FaceProfileEntity;
import com.web.labportalbackend.face.entity.FaceSecurityConfigEntity;
import com.web.labportalbackend.face.enums.FaceProfileStatus;
import com.web.labportalbackend.face.repository.FaceConsentLogRepository;
import com.web.labportalbackend.face.repository.FaceProfileRepository;
import com.web.labportalbackend.face.repository.FaceSecurityConfigRepository;
import com.web.labportalbackend.face.security.FaceEmbeddingCipher;
import com.web.labportalbackend.face.service.FaceCheckinService;
import com.web.labportalbackend.face.service.FaceCheckinWriter;
import jakarta.persistence.EntityNotFoundException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FaceCheckinServiceImpl implements FaceCheckinService {

    private static final Set<String> FAILURE_RESULTS = Set.of(
            "NO_FACE", "MULTIPLE_FACES", "LOW_QUALITY", "NO_MATCH", "SPOOF_DETECTED", "SERVICE_ERROR");

    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final FaceConsentLogRepository consentRepository;
    private final FaceProfileRepository profileRepository;
    private final FaceSecurityConfigRepository securityConfigRepository;
    private final SystemConfigService systemConfigService;
    private final FaceProcessingClient processingClient;
    private final FaceEmbeddingCipher embeddingCipher;
    private final FaceCheckinWriter writer;
    private final ObjectMapper objectMapper;

    @Override
    public FaceCheckinResponse checkIn(FaceCheckinRequest request) {
        User actor = currentStudent();
        Booking booking = ownedBooking(actor, request.bookingId());
        validateBookingAndWindow(booking, Instant.now());
        requireGrantedConsent(actor.getId());
        FaceProfileEntity profile = profileRepository
                .findByUserIdAndProfileStatusAndActiveTrueAndDeletedFalse(actor.getId(), FaceProfileStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("An active face profile is required"));
        FaceSecurityConfigEntity config = securityConfig();
        if (!Boolean.TRUE.equals(config.getFaceEnabled())) {
            throw new IllegalStateException("Face check-in is disabled");
        }
        validateImage(request.imageBase64());

        FaceMatchResponse match;
        try {
            match = processingClient.match(new FaceMatchRequest(
                    request.imageBase64(),
                    request.contentType(),
                    decryptEmbedding(profile.getEncryptedEmbedding()),
                    config.getConfidenceThreshold().doubleValue(),
                    config.getLivenessThreshold().doubleValue(),
                    Boolean.TRUE.equals(config.getLivenessRequired())));
        } catch (FaceServiceException exception) {
            if (exception.retryable()) {
                writer.recordFailure(actor.getId(), request.bookingId(), null, null, "SERVICE_ERROR");
            }
            throw exception;
        }
        return decideAndPersist(actor.getId(), request.bookingId(), match, config);
    }

    private FaceCheckinResponse decideAndPersist(
            Long actorId,
            Long bookingId,
            FaceMatchResponse match,
            FaceSecurityConfigEntity config
    ) {
        if (match == null || match.result() == null || !validScore(match.confidenceScore())
                || !validScore(match.livenessScore())) {
            throw new IllegalStateException("Face service returned an invalid match result");
        }
        boolean confidencePassed = match.confidenceScore() != null
                && match.confidenceScore() >= config.getConfidenceThreshold().doubleValue();
        boolean livenessPassed = !Boolean.TRUE.equals(config.getLivenessRequired())
                || (match.livenessScore() != null
                && match.livenessScore() >= config.getLivenessThreshold().doubleValue());
        if ("MATCH".equals(match.result()) && confidencePassed && livenessPassed) {
            Instant checkedInAt = writer.complete(actorId, bookingId,
                    match.confidenceScore(), match.livenessScore());
            return new FaceCheckinResponse(bookingId, true, "MATCH", match.confidenceScore(),
                    match.livenessScore(), null, checkedInAt);
        }

        String failureReason = failureReason(match, confidencePassed, livenessPassed);
        writer.recordFailure(actorId, bookingId, match.confidenceScore(), match.livenessScore(), failureReason);
        return new FaceCheckinResponse(bookingId, false, failureReason, match.confidenceScore(),
                match.livenessScore(), failureReason, null);
    }

    private String failureReason(FaceMatchResponse match, boolean confidencePassed, boolean livenessPassed) {
        if (match.failureReason() != null && FAILURE_RESULTS.contains(match.failureReason())) {
            return match.failureReason();
        }
        if (FAILURE_RESULTS.contains(match.result())) {
            return match.result();
        }
        if (!livenessPassed) {
            return "SPOOF_DETECTED";
        }
        return !confidencePassed ? "NO_MATCH" : "SERVICE_ERROR";
    }

    private User currentStudent() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication is required");
        }
        User actor = userRepository.findByEmailOrUsername(authentication.getName(), authentication.getName())
                .orElseThrow(() -> new AccessDeniedException("Authenticated user not found"));
        if (!actor.hasRole("STUDENT")) {
            throw new AccessDeniedException("Face check-in is limited to students");
        }
        return actor;
    }

    private Booking ownedBooking(User actor, Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new EntityNotFoundException("Booking not found"));
        if (!booking.getUser().getId().equals(actor.getId())) {
            throw new AccessDeniedException("Face check-in is limited to the booking owner");
        }
        return booking;
    }

    private void validateBookingAndWindow(Booking booking, Instant now) {
        if (booking.getStatus() != BookingStatus.APPROVED) {
            throw new IllegalStateException("Only an approved booking can be checked in");
        }
        if (booking.getTimeSlot() == null || booking.getTimeSlot().getStatus() == TimeSlotStatus.CANCELLED) {
            throw new IllegalStateException("Booking time slot is not available");
        }
        SystemConfigResponse config = systemConfigService.getConfig();
        if (config == null || config.booking() == null || config.booking().checkinWindowMinutes() <= 0) {
            throw new IllegalStateException("Check-in window is not configured");
        }
        Instant end = booking.getStartTime().plus(Duration.ofMinutes(config.booking().checkinWindowMinutes()));
        if (now.isBefore(booking.getStartTime()) || now.isAfter(end)) {
            throw new IllegalStateException("Booking is outside the check-in window");
        }
    }

    private void requireGrantedConsent(Long userId) {
        boolean granted = consentRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(userId)
                .map(FaceConsentLogEntity::getConsentStatus)
                .map(status -> status.allowsActiveProfile())
                .orElse(false);
        if (!granted) {
            throw new IllegalStateException("Granted face consent is required");
        }
    }

    private FaceSecurityConfigEntity securityConfig() {
        List<FaceSecurityConfigEntity> configs = securityConfigRepository.findAllByActiveTrueAndDeletedFalse();
        if (configs.size() != 1) {
            throw new IllegalStateException("Exactly one active face security configuration is required");
        }
        return configs.getFirst();
    }

    private List<Double> decryptEmbedding(String encryptedEmbedding) {
        try {
            List<Double> embedding = objectMapper.readValue(
                    embeddingCipher.decrypt(encryptedEmbedding), new TypeReference<>() { });
            if (embedding.isEmpty() || embedding.size() > 4096
                    || embedding.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
                throw new IllegalStateException("Stored face embedding is invalid");
            }
            return embedding;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored face embedding is invalid", exception);
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

    private boolean validScore(Double score) {
        return score == null || (Double.isFinite(score) && score >= 0 && score <= 1);
    }
}
