package com.web.labportalbackend.research.repository;

import com.web.labportalbackend.research.entity.EvaluationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EvaluationRepository extends JpaRepository<EvaluationEntity, Long> {
    List<EvaluationEntity> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    List<EvaluationEntity> findByProjectIdAndDeletedFalseAndActiveTrueOrderByCreatedAtDesc(Long projectId);

    List<EvaluationEntity> findByProjectIdAndStudentIdAndDeletedFalseAndActiveTrueOrderByCreatedAtDesc(
            Long projectId,
            Long studentId
    );

    Optional<EvaluationEntity> findFirstByProjectIdAndStudentIdAndDeletedFalseAndActiveTrueOrderByCreatedAtDesc(
            Long projectId,
            Long studentId
    );
}
