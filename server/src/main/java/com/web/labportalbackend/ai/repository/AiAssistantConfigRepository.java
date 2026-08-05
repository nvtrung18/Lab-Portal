package com.web.labportalbackend.ai.repository;

import com.web.labportalbackend.ai.entity.AiAssistantConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiAssistantConfigRepository extends JpaRepository<AiAssistantConfigEntity, Long> {
}
