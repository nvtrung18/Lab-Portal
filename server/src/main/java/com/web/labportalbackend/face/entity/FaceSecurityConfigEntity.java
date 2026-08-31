package com.web.labportalbackend.face.entity;

import com.web.labportalbackend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "face_security_config", uniqueConstraints = {
        @UniqueConstraint(name = "uk_face_security_config_key", columnNames = "config_key")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FaceSecurityConfigEntity extends BaseEntity {

    @Column(name = "config_key", nullable = false, length = 50)
    private String configKey;

    @Column(name = "face_enabled", nullable = false)
    private Boolean faceEnabled;

    @Column(name = "confidence_threshold", nullable = false, precision = 5, scale = 4)
    private BigDecimal confidenceThreshold;

    @Column(name = "liveness_threshold", nullable = false, precision = 5, scale = 4)
    private BigDecimal livenessThreshold;

    @Column(name = "liveness_required", nullable = false)
    private Boolean livenessRequired;

    @Column(name = "qr_when_face_disabled", nullable = false)
    private Boolean qrWhenFaceDisabled;

    @Column(name = "qr_when_service_unavailable", nullable = false)
    private Boolean qrWhenServiceUnavailable;

    @Column(name = "qr_when_profile_unavailable", nullable = false)
    private Boolean qrWhenProfileUnavailable;

    @Column(name = "manual_override_enabled", nullable = false)
    private Boolean manualOverrideEnabled;
}
