package com.web.labportalbackend.research.repository;

import com.web.labportalbackend.research.entity.ResearchTopicEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResearchTopicRepository extends JpaRepository<ResearchTopicEntity, Long> {

    @EntityGraph(attributePaths = {"lab", "manager", "createdBy"})
    List<ResearchTopicEntity> findByLabIdAndDeletedFalseAndActiveTrue(Long labId);

    @EntityGraph(attributePaths = {"lab", "manager", "createdBy"})
    Optional<ResearchTopicEntity> findByIdAndDeletedFalseAndActiveTrue(Long id);
}
