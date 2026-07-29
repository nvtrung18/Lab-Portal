package com.web.labportalbackend.research.repository;

import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.research.enums.GroupStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupRepository extends JpaRepository<GroupEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
            SELECT g
            FROM GroupEntity g
            WHERE g.id = :id
              AND g.deleted = false
              AND g.active = true
            """)
    Optional<GroupEntity> findByIdForStatusAuthorization(@Param("id") Long id);

    @EntityGraph(attributePaths = {"lab", "topic", "leader", "members", "members.user"})
    List<GroupEntity> findByLabIdAndDeletedFalseAndActiveTrue(Long labId);

    @EntityGraph(attributePaths = {"lab", "topic", "leader", "members", "members.user"})
    List<GroupEntity> findByTopicIdAndDeletedFalseAndActiveTrue(Long topicId);

    @EntityGraph(attributePaths = {"lab", "topic", "project", "leader", "members", "members.user"})
    List<GroupEntity> findByProjectIdAndDeletedFalseAndActiveTrue(Long projectId);

    @EntityGraph(attributePaths = {"lab", "lab.manager", "topic", "topic.manager", "project", "project.manager", "leader", "members", "members.user"})
    Optional<GroupEntity> findByIdAndDeletedFalseAndActiveTrue(Long id);

    long countByTopicIdAndDeletedFalseAndActiveTrue(Long topicId);

    @Query("SELECT COUNT(g) FROM GroupEntity g WHERE g.status = :status AND g.deleted = false AND g.active = true")
    long countActiveByStatus(@Param("status") GroupStatus status);
}
