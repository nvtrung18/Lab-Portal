package com.web.labportalbackend.research.repository;

import com.web.labportalbackend.research.entity.GroupEntity;
import com.web.labportalbackend.ai.service.AiResearchContext;
import org.springframework.data.domain.Pageable;
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

    @Query("""
            SELECT new com.web.labportalbackend.ai.service.AiResearchContext$Group(g.id, g.name, gm.role)
            FROM GroupEntity g JOIN g.lab l LEFT JOIN GroupMemberEntity gm
                 ON gm.group.id = g.id AND gm.user.id = :actorId AND gm.active = true AND gm.deleted = false
            WHERE g.active = true AND g.deleted = false AND l.active = true AND l.deleted = false
              AND EXISTS (SELECT p.id FROM ProjectEntity p JOIN p.lab pl
                          WHERE p.id = :projectId AND p.active = true AND p.deleted = false
                            AND pl.active = true AND pl.deleted = false AND pl.id = l.id
                            AND (g.project.id = p.id OR p.group.id = g.id)
                            AND (g.project IS NULL OR g.project.id = p.id)
                            AND (p.group IS NULL OR p.group.id = g.id))
              AND (:selectedGroupId IS NULL OR g.id = :selectedGroupId)
              AND EXISTS (SELECT r.id FROM User roleActor JOIN roleActor.roles r
                          WHERE roleActor.id = :actorId AND r.name = :selectedRoleName)
              AND ((:selectedRoleName = 'LAB_MANAGER' AND l.manager.id = :actorId)
                   OR (:selectedRoleName = 'STUDENT' AND gm.id IS NOT NULL))
              AND EXISTS (SELECT a.id FROM User a WHERE a.id = :actorId AND a.active = true
                          AND a.deleted = false AND a.status = com.web.labportalbackend.common.enums.UserStatus.ACTIVE)
            ORDER BY g.id ASC
            """)
    List<AiResearchContext.Group> findAiContextGroups(@Param("actorId") Long actorId,
                                                        @Param("projectId") Long projectId,
                                                        @Param("selectedGroupId") Long selectedGroupId,
                                                        Pageable pageable, @Param("selectedRoleName") String selectedRoleName);

    @Query("""
            SELECT new com.web.labportalbackend.ai.service.AiResearchContext$Group(g.id, g.name, gm.role)
            FROM GroupEntity g JOIN g.lab l LEFT JOIN GroupMemberEntity gm
                 ON gm.group.id = g.id AND gm.user.id = :actorId AND gm.active = true AND gm.deleted = false
            WHERE g.id = :groupId AND g.active = true AND g.deleted = false AND l.active = true AND l.deleted = false
              AND EXISTS (SELECT p.id FROM ProjectEntity p JOIN p.lab pl
                          WHERE p.id = :projectId AND p.active = true AND p.deleted = false
                            AND pl.active = true AND pl.deleted = false AND pl.id = l.id
                            AND (g.project.id = p.id OR p.group.id = g.id)
                            AND (g.project IS NULL OR g.project.id = p.id)
                            AND (p.group IS NULL OR p.group.id = g.id))
              AND EXISTS (SELECT r.id FROM User roleActor JOIN roleActor.roles r
                          WHERE roleActor.id = :actorId AND r.name = :selectedRoleName)
              AND ((:selectedRoleName = 'LAB_MANAGER' AND l.manager.id = :actorId)
                   OR (:selectedRoleName = 'STUDENT' AND gm.id IS NOT NULL))
              AND EXISTS (SELECT a.id FROM User a WHERE a.id = :actorId AND a.active = true
                          AND a.deleted = false AND a.status = com.web.labportalbackend.common.enums.UserStatus.ACTIVE)
            """)
    Optional<AiResearchContext.Group> findAiContextGroup(@Param("actorId") Long actorId,
                                                           @Param("projectId") Long projectId,
                                                           @Param("groupId") Long groupId,
                                                           @Param("selectedRoleName") String selectedRoleName);
}
