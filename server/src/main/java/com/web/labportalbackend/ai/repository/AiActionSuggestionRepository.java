package com.web.labportalbackend.ai.repository;

import com.web.labportalbackend.ai.entity.AiActionSuggestionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface AiActionSuggestionRepository extends JpaRepository<AiActionSuggestionEntity, Long>,
        JpaSpecificationExecutor<AiActionSuggestionEntity> {
}
