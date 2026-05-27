package com.web.labportalbackend.auth.entity;

import com.web.labportalbackend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "verification_codes", indexes = {
        @Index(name = "idx_verification_email_type", columnList = "email,type"),
        @Index(name = "idx_verification_expires_at", columnList = "expires_at")
})
@Getter
@Setter
public class VerificationCode extends BaseEntity {
    @Column(nullable = false, length = 100)
    private String email;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(nullable = false, length = 40)
    private String type;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Boolean used = false;
}
