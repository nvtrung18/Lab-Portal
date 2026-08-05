package com.web.labportalbackend.ai.entity;

import com.web.labportalbackend.ai.enums.AiAssistantKey;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ai_usage_log", indexes = {
        @Index(name = "idx_ai_usage_log_user_created", columnList = "user_id, created_at, id"),
        @Index(name = "idx_ai_usage_log_assistant_role_module_created",
                columnList = "assistant_key, role, module, created_at, id"),
        @Index(name = "idx_ai_usage_log_lab_created", columnList = "lab_id, created_at, id"),
        @Index(name = "idx_ai_usage_log_project_created", columnList = "project_id, created_at, id"),
        @Index(name = "idx_ai_usage_log_group_created", columnList = "group_id, created_at, id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiUsageLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "assistant_key", nullable = false, length = 50)
    private AiAssistantKey assistantKey;

    @Column(nullable = false, length = 50)
    private String role;

    @Column(nullable = false, length = 100)
    private String module;

    @Column(name = "lab_id")
    private Long labId;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "group_id")
    private Long groupId;

    @Builder.Default
    @Column(name = "prompt_tokens", nullable = false)
    private Integer promptTokens = 0;

    @Builder.Default
    @Column(name = "completion_tokens", nullable = false)
    private Integer completionTokens = 0;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;

    @Builder.Default
    @Column(nullable = false)
    private Boolean deleted = false;

    @PrePersist
    private void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (active == null) {
            active = true;
        }
        if (deleted == null) {
            deleted = false;
        }
    }
}
