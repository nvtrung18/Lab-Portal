package com.web.labportalbackend.ai.rag.entity;

import com.web.labportalbackend.ai.enums.AiAssistantDomain;
import com.web.labportalbackend.ai.rag.enums.AiRagVisibility;
import com.web.labportalbackend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ai_rag_document", indexes = {
        @Index(name = "idx_ai_rag_document_namespace_active", columnList = "namespace, active, deleted"),
        @Index(name = "idx_ai_rag_document_scope", columnList = "namespace, lab_id, project_id, group_id, active, deleted"),
        @Index(name = "idx_ai_rag_document_owner", columnList = "owner_id, active, deleted")
})
@Getter
@Setter
@NoArgsConstructor
public class AiRagDocumentEntity extends BaseEntity {

    @Column(nullable = false, length = 64)
    private String namespace;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiAssistantDomain domain;

    @Column(name = "resource_id", nullable = false, length = 128)
    private String resourceId;

    @Column(name = "document_version", nullable = false)
    private Integer documentVersion;

    @Column(name = "source_type", nullable = false, length = 64)
    private String sourceType;

    @Column(nullable = false, length = 255)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AiRagVisibility visibility;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "lab_id")
    private Long labId;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "group_id")
    private Long groupId;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;
}
