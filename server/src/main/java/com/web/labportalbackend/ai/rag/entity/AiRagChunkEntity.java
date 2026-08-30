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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ai_rag_chunk", indexes = {
        @Index(name = "idx_ai_rag_chunk_namespace_active", columnList = "namespace, active, deleted, document_id, chunk_index"),
        @Index(name = "idx_ai_rag_chunk_scope", columnList = "namespace, lab_id, project_id, group_id, active, deleted"),
        @Index(name = "idx_ai_rag_chunk_owner", columnList = "owner_id, active, deleted")
}, uniqueConstraints = @UniqueConstraint(name = "uk_ai_rag_chunk_position", columnNames = {"document_id", "chunk_index"}))
@Getter
@Setter
@NoArgsConstructor
public class AiRagChunkEntity extends BaseEntity {

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(nullable = false, length = 64)
    private String namespace;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiAssistantDomain domain;

    @Column(name = "resource_id", nullable = false, length = 128)
    private String resourceId;

    @Column(name = "document_version", nullable = false)
    private Integer documentVersion;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(name = "source_type", nullable = false, length = 64)
    private String sourceType;

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

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
}
