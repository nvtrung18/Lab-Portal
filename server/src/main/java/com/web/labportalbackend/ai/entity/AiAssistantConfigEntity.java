package com.web.labportalbackend.ai.entity;

import com.web.labportalbackend.ai.enums.AiAssistantKey;
import com.web.labportalbackend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ai_assistant_config", uniqueConstraints = {
        @UniqueConstraint(name = "uk_ai_assistant_config_assistant_key", columnNames = "assistant_key")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiAssistantConfigEntity extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "assistant_key", nullable = false, length = 50)
    private AiAssistantKey assistantKey;

    @Builder.Default
    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "system_prompt_key", nullable = false, length = 100)
    private String systemPromptKey;

    @Column(name = "max_requests_per_day", nullable = false)
    private Integer maxRequestsPerDay;

    @Column(name = "max_context_tokens", nullable = false)
    private Integer maxContextTokens;
}
