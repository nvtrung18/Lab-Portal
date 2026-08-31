package com.web.labportalbackend.ai.rag.repository;

import com.web.labportalbackend.ai.rag.entity.AiRagChunkEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public interface AiRagChunkRepository extends JpaRepository<AiRagChunkEntity, Long> {

    List<AiRagChunkEntity> findByDocumentIdAndActiveTrueAndDeletedFalseOrderByChunkIndex(Long documentId);

    List<AiRagChunkEntity> findByDocumentIdAndDeletedFalseOrderByChunkIndex(Long documentId);

    List<AiRagChunkEntity> findByNamespaceAndActiveTrueAndDeletedFalse(String namespace, Pageable pageable);
}
