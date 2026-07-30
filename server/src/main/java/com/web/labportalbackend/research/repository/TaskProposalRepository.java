package com.web.labportalbackend.research.repository;

import com.web.labportalbackend.research.entity.TaskProposalEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TaskProposalRepository extends JpaRepository<TaskProposalEntity, Long> {
    Optional<TaskProposalEntity> findByIdAndDeletedFalseAndActiveTrue(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p
            FROM TaskProposalEntity p
            WHERE p.id = :id
              AND p.deleted = false
              AND p.active = true
            """)
    Optional<TaskProposalEntity> findByIdForReview(@Param("id") Long id);
}
