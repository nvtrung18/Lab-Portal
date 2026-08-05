package com.web.labportalbackend.ai.entity;

import com.web.labportalbackend.ai.enums.AiActionSuggestionStatus;
import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ai_action_suggestion", indexes = {
        @Index(name = "idx_ai_action_suggestion_requested_status_created",
                columnList = "requested_by, status, created_at, id"),
        @Index(name = "idx_ai_action_suggestion_assistant_status_created",
                columnList = "assistant_key, status, created_at, id"),
        @Index(name = "idx_ai_action_suggestion_target", columnList = "target_module, target_id"),
        @Index(name = "idx_ai_action_suggestion_executed_created", columnList = "executed_by, executed_at, id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiActionSuggestionEntity extends BaseEntity {

    @Column(name = "requested_by", nullable = false)
    private Long requestedById;

    @Enumerated(EnumType.STRING)
    @Column(name = "assistant_key", nullable = false, length = 50)
    private AiAssistantKey assistantKey;

    @Column(name = "action_type", nullable = false, length = 100)
    private String actionType;

    @Column(name = "target_module", nullable = false, length = 100)
    private String targetModule;

    @Column(name = "target_id")
    private Long targetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "json")
    private String payloadJson;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiActionSuggestionStatus status = AiActionSuggestionStatus.PENDING;

    @Column(name = "executed_by")
    private Long executedById;

    @Column(name = "rejected_reason", columnDefinition = "TEXT")
    private String rejectedReason;

    @Column(name = "executed_at")
    private Instant executedAt;
}
