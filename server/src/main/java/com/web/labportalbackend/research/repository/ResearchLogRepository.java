package com.web.labportalbackend.research.repository;

import com.web.labportalbackend.research.entity.ResearchLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ResearchLogRepository extends JpaRepository<ResearchLogEntity, Long>,
        JpaSpecificationExecutor<ResearchLogEntity> {
}
