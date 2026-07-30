package com.web.labportalbackend.research.repository;

import com.web.labportalbackend.research.entity.TaskProposalEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TaskProposalRepository extends JpaRepository<TaskProposalEntity, Long> {
    Optional<TaskProposalEntity> findByIdAndDeletedFalseAndActiveTrue(Long id);
}
