package com.web.labportalbackend.ai.entity;

import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ai_conversation", indexes = {
        @Index(name = "idx_ai_conversation_user_assistant_module_updated",
                columnList = "user_id, assistant_key, module_context, updated_at, id"),
        @Index(name = "idx_ai_conversation_lab_updated", columnList = "lab_id, updated_at, id"),
        @Index(name = "idx_ai_conversation_project_updated", columnList = "project_id, updated_at, id"),
        @Index(name = "idx_ai_conversation_group_updated", columnList = "group_id, updated_at, id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiConversationEntity extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "assistant_key", nullable = false, length = 50)
    private AiAssistantKey assistantKey;

    @Column(name = "module_context", nullable = false, length = 100)
    private String moduleContext;

    @Column(name = "lab_id")
    private Long labId;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "group_id")
    private Long groupId;

    @Column(nullable = false, length = 255)
    private String title;
}
