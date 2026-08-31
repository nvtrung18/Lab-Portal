package com.web.labportalbackend.face.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.web.labportalbackend.face.enums.FaceConsentStatus;
import com.web.labportalbackend.face.enums.FaceProfileStatus;
import org.junit.jupiter.api.Test;

class FaceProfileLifecycleTest {

    @Test
    void onlyGrantedConsentCanCreateAnActiveProfile() {
        for (FaceConsentStatus status : FaceConsentStatus.values()) {
            FaceProfileEntity profile = FaceProfileEntity.builder().build();
            if (status == FaceConsentStatus.GRANTED) {
                profile.replaceEmbedding("encrypted-base64", "face-model-v1", status);
                assertEquals(FaceProfileStatus.ACTIVE, profile.getProfileStatus());
                assertTrue(profile.getActive());
            } else {
                assertThrows(IllegalStateException.class,
                        () -> profile.replaceEmbedding("encrypted-base64", "face-model-v1", status));
            }
        }
    }

    @Test
    void disableAndDeleteCannotLeaveAnActiveProfile() {
        FaceProfileEntity profile = FaceProfileEntity.builder().build();
        profile.replaceEmbedding("encrypted-base64", "face-model-v1", FaceConsentStatus.GRANTED);

        profile.disable();
        assertEquals(FaceProfileStatus.DISABLED, profile.getProfileStatus());
        assertFalse(profile.getActive());

        profile.markDeleted("encrypted-tombstone-base64");
        assertEquals(FaceProfileStatus.DELETED, profile.getProfileStatus());
        assertEquals("encrypted-tombstone-base64", profile.getEncryptedEmbedding());
        assertFalse(profile.getActive());
        assertTrue(profile.getDeleted());
    }
}
