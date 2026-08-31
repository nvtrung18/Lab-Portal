package com.web.labportalbackend.notification.entity;

import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.notification.enums.NotificationEventType;
import com.web.labportalbackend.notification.enums.NotificationTargetModule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_recipient_read_created",
                columnList = "recipient_id, is_read, created_at, id"),
        @Index(name = "idx_notifications_recipient_created",
                columnList = "recipient_id, created_at, id"),
        @Index(name = "idx_notifications_target", columnList = "target_module, target_id"),
        @Index(name = "idx_notifications_assistant_created", columnList = "assistant_key, created_at, id")
})
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 100)
    private NotificationEventType eventType;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_module", nullable = false, length = 50)
    private NotificationTargetModule targetModule;

    @Column(name = "target_id")
    private Long targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "assistant_key", length = 50)
    private AiAssistantKey assistantKey;

    @Builder.Default
    @Column(name = "is_read", nullable = false)
    private Boolean read = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public void markRead() {
        read = true;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (read == null) {
            read = false;
        }
        if (title == null || title.isBlank() || message == null || message.isBlank()) {
            throw new IllegalStateException("Notification title and message are required");
        }
    }
}
