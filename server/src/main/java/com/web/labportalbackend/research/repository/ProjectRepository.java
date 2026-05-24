package com.web.labportalbackend.research.repository;

import com.web.labportalbackend.research.entity.ProjectEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<ProjectEntity, Long> {

    @EntityGraph(attributePaths = {"group", "topic", "manager", "createdBy"})
    List<ProjectEntity> findByGroupIdAndDeletedFalseAndActiveTrue(Long groupId);

    @EntityGraph(attributePaths = "group")
    List<ProjectEntity> findByGroupId(Long groupId);

    @EntityGraph(attributePaths = {"group", "group.lab", "topic", "manager", "createdBy"})
    Optional<ProjectEntity> findByIdAndDeletedFalseAndActiveTrue(Long id);

    long countByGroupIdAndDeletedFalseAndActiveTrue(Long groupId);
}
