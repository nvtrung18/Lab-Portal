package com.web.labportalbackend.ai.repository;

import com.web.labportalbackend.ai.entity.AiUsageLogEntity;
import com.web.labportalbackend.ai.enums.AiAssistantKey;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiUsageLogRepository extends JpaRepository<AiUsageLogEntity, Long> {

    long countByUserIdAndAssistantKeyAndCreatedAtGreaterThanEqualAndCreatedAtLessThanAndActiveTrueAndDeletedFalse(
            Long userId, AiAssistantKey assistantKey, Instant startInclusive, Instant endExclusive);

    long countByUserIdAndAssistantKeyAndRoleAndModuleAndCreatedAtGreaterThanEqualAndCreatedAtLessThanAndActiveTrueAndDeletedFalse(
            Long userId, AiAssistantKey assistantKey, String role, String module, Instant startInclusive,
            Instant endExclusive);
}
