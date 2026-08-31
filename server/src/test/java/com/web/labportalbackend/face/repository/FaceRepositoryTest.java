package com.web.labportalbackend.face.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.enums.UserStatus;
import com.web.labportalbackend.face.entity.FaceConsentLogEntity;
import com.web.labportalbackend.face.entity.FaceProfileEntity;
import com.web.labportalbackend.face.enums.FaceConsentStatus;
import com.web.labportalbackend.face.enums.FaceProfileStatus;
import java.time.Instant;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

@DataJpaTest
class FaceRepositoryTest {

    @Autowired UserRepository userRepository;
    @Autowired FaceConsentLogRepository consentLogRepository;
    @Autowired FaceProfileRepository profileRepository;

    @Test
    void consentLookupReturnsOnlyTheOwnersLatestState() {
        User owner = saveUser("owner");
        User other = saveUser("other");
        consentLogRepository.saveAndFlush(consent(owner, FaceConsentStatus.GRANTED,
                Instant.parse("2026-08-01T00:00:00Z")));
        consentLogRepository.saveAndFlush(consent(owner, FaceConsentStatus.WITHDRAWN,
                Instant.parse("2026-08-02T00:00:00Z")));
        consentLogRepository.saveAndFlush(consent(other, FaceConsentStatus.GRANTED,
                Instant.parse("2026-08-03T00:00:00Z")));

        assertEquals(FaceConsentStatus.WITHDRAWN,
                consentLogRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(owner.getId())
                        .orElseThrow().getConsentStatus());
    }

    @Test
    void activeProfileLookupIsOwnerScopedAndExcludesDisabledOrDeletedRows() {
        User owner = saveUser("profile-owner");
        FaceProfileEntity profile = FaceProfileEntity.builder().user(owner).build();
        profile.replaceEmbedding("encrypted-base64", "face-model-v1", FaceConsentStatus.GRANTED);
        profileRepository.saveAndFlush(profile);

        assertTrue(profileRepository.findByUserIdAndProfileStatusAndActiveTrueAndDeletedFalse(
                owner.getId(), FaceProfileStatus.ACTIVE).isPresent());

        profile.disable();
        profileRepository.saveAndFlush(profile);
        assertFalse(profileRepository.findByUserIdAndProfileStatusAndActiveTrueAndDeletedFalse(
                owner.getId(), FaceProfileStatus.ACTIVE).isPresent());
    }

    private User saveUser(String name) {
        return userRepository.saveAndFlush(new User(name + "@example.test", name, "password", name,
                null, UserStatus.ACTIVE, new HashSet<>()));
    }

    private FaceConsentLogEntity consent(User user, FaceConsentStatus status, Instant createdAt) {
        return FaceConsentLogEntity.builder().user(user).changedBy(user).consentStatus(status)
                .createdAt(createdAt).build();
    }
}
