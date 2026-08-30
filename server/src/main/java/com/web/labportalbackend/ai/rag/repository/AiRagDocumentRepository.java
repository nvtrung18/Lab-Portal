package com.web.labportalbackend.ai.rag.repository;

import com.web.labportalbackend.ai.rag.entity.AiRagDocumentEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiRagDocumentRepository extends JpaRepository<AiRagDocumentEntity, Long> {

    boolean existsByNamespaceAndResourceIdAndActiveTrueAndDeletedFalse(String namespace, String resourceId);

    Optional<AiRagDocumentEntity> findByIdAndActiveTrueAndDeletedFalse(Long id);
}
