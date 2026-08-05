package com.web.labportalbackend.ai.repository;

import com.web.labportalbackend.ai.entity.AiQuotaConfigEntity;
import com.web.labportalbackend.ai.enums.AiAssistantKey;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiQuotaConfigRepository extends JpaRepository<AiQuotaConfigEntity, Long> {

    Optional<AiQuotaConfigEntity> findByAssistantKeyAndRoleAndModuleAndActiveTrueAndDeletedFalse(
            AiAssistantKey assistantKey, String role, String module);
}
