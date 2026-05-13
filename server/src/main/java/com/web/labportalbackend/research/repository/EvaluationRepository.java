package com.web.labportalbackend.research.repository;

import com.web.labportalbackend.research.entity.EvaluationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EvaluationRepository extends JpaRepository<EvaluationEntity, Long> {
    List<EvaluationEntity> findByProjectIdOrderByCreatedAtDesc(Long projectId);
}
