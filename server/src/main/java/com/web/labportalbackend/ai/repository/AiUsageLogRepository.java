package com.web.labportalbackend.ai.repository;

import com.web.labportalbackend.ai.entity.AiUsageLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiUsageLogRepository extends JpaRepository<AiUsageLogEntity, Long> {
}
