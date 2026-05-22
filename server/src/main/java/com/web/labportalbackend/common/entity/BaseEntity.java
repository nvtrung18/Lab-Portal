package com.web.labportalbackend.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serializable;
import java.time.Instant;

/**
 * Abstract base entity providing common audit fields for all JPA entities.
 * <p>
 * Uses {@link Instant} (UTC epoch-based) instead of {@link java.time.LocalDateTime}
 * to guarantee timezone-safe storage and retrieval across all environments.
 * <p>
 * Uses {@code @Getter/@Setter} instead of {@code @Data} to avoid Lombok-generated
 * {@code equals()}, {@code hashCode()}, and {@code toString()} which are problematic
 * with JPA proxies and lazy-loaded collections.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    private Boolean active = true;

    /**
     * Soft-delete flag. Entities are never physically removed;
     * they are marked as deleted and filtered out at query level.
     */
    @Column(nullable = false)
    private Boolean deleted = false;

    @PrePersist
    protected void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (active == null) {
            active = true;
        }
        if (deleted == null) {
            deleted = false;
        }
    }

    @PreUpdate
    protected void preUpdate() {
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    // --- equals & hashCode based on ID for JPA best practices ---

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BaseEntity that = (BaseEntity) o;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        // Use a constant so that hash code is consistent before and after persist
        return getClass().hashCode();
    }
}
