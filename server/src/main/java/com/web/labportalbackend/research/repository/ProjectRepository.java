package com.web.labportalbackend.research.repository;

import com.web.labportalbackend.research.entity.ProjectEntity;
import com.web.labportalbackend.research.enums.ProjectStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<ProjectEntity, Long> {

    @EntityGraph(attributePaths = {"group", "topic", "manager", "createdBy"})
    List<ProjectEntity> findByGroupIdAndDeletedFalseAndActiveTrue(Long groupId);

    @EntityGraph(attributePaths = {"lab", "group", "topic", "manager", "createdBy"})
    List<ProjectEntity> findByLabIdAndDeletedFalseAndActiveTrue(Long labId);

    @EntityGraph(attributePaths = "group")
    List<ProjectEntity> findByGroupId(Long groupId);

    @EntityGraph(attributePaths = {"lab", "group", "group.lab", "topic", "manager", "createdBy"})
    Optional<ProjectEntity> findByIdAndDeletedFalseAndActiveTrue(Long id);

    long countByGroupIdAndDeletedFalseAndActiveTrue(Long groupId);

    @Query("SELECT COUNT(p) FROM ProjectEntity p WHERE p.status = :status AND p.deleted = false AND p.active = true")
    long countActiveByStatus(@Param("status") ProjectStatus status);

    @Query("SELECT COUNT(p) FROM ProjectEntity p WHERE p.lab.id = :labId AND p.status = :status AND p.deleted = false AND p.active = true")
    long countActiveByLabIdAndStatus(
            @Param("labId") Long labId,
            @Param("status") ProjectStatus status
    );
}

