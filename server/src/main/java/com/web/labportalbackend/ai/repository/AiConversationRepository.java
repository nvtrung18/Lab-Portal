package com.web.labportalbackend.ai.repository;

import com.web.labportalbackend.ai.entity.AiConversationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiConversationRepository extends JpaRepository<AiConversationEntity, Long> {
}
