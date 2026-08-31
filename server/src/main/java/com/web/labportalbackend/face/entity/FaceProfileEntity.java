package com.web.labportalbackend.face.entity;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.common.entity.BaseEntity;
import com.web.labportalbackend.face.enums.FaceConsentStatus;
import com.web.labportalbackend.face.enums.FaceProfileStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "face_profile", uniqueConstraints = {
        @UniqueConstraint(name = "uk_face_profile_user", columnNames = "user_id")
})
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FaceProfileEntity extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "encrypted_embedding", nullable = false, columnDefinition = "TEXT")
    private String encryptedEmbedding;

    @Column(name = "embedding_model", nullable = false, length = 100)
    private String embeddingModel;

    @Enumerated(EnumType.STRING)
    @Column(name = "profile_status", nullable = false, length = 20)
    private FaceProfileStatus profileStatus;

    public void replaceEmbedding(String encryptedEmbedding, String embeddingModel,
                                 FaceConsentStatus currentConsent) {
        requireGrantedConsent(currentConsent);
        if (encryptedEmbedding == null || encryptedEmbedding.isBlank()
                || embeddingModel == null || embeddingModel.isBlank()) {
            throw new IllegalArgumentException("Encrypted embedding and model are required");
        }
        this.encryptedEmbedding = encryptedEmbedding;
        this.embeddingModel = embeddingModel;
        this.profileStatus = FaceProfileStatus.ACTIVE;
        setActive(true);
        setDeleted(false);
    }

    public void disable() {
        if (profileStatus != FaceProfileStatus.DELETED) {
            profileStatus = FaceProfileStatus.DISABLED;
            setActive(false);
        }
    }

    public void markDeleted(String encryptedTombstone) {
        if (encryptedTombstone == null || encryptedTombstone.isBlank()) {
            throw new IllegalArgumentException("Encrypted tombstone is required");
        }
        profileStatus = FaceProfileStatus.DELETED;
        encryptedEmbedding = encryptedTombstone;
        setActive(false);
        setDeleted(true);
    }

    private static void requireGrantedConsent(FaceConsentStatus currentConsent) {
        if (currentConsent == null || !currentConsent.allowsActiveProfile()) {
            throw new IllegalStateException("Granted face consent is required for an active profile");
        }
    }
}
