package com.web.labportalbackend.ai.repository;

import com.web.labportalbackend.ai.entity.AiMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiMessageRepository extends JpaRepository<AiMessageEntity, Long> {
}
