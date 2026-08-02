package com.web.labportalbackend.research.repository;

import com.web.labportalbackend.research.entity.TaskProposalEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import com.web.labportalbackend.research.enums.TaskProposalStatus;

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

    @Query(value = """
            SELECT p FROM TaskProposalEntity p
            JOIN ProjectEntity project ON project.id = p.projectId
            JOIN GroupEntity grp ON grp.id = p.groupId
            JOIN project.lab projectLab JOIN grp.lab groupLab
            WHERE p.active = true AND p.deleted = false
              AND project.active = true AND project.deleted = false
              AND grp.active = true AND grp.deleted = false
              AND projectLab.active = true AND projectLab.deleted = false
              AND groupLab.active = true AND groupLab.deleted = false
              AND projectLab.id = groupLab.id
              AND (grp.project IS NULL OR grp.project.id = project.id)
              AND (project.group IS NULL OR project.group.id = grp.id)
              AND (grp.project.id = project.id OR project.group.id = grp.id)
              AND ((:studentAuthorized = true AND (p.proposedById = :actorId OR EXISTS (
                    SELECT gm.id FROM GroupMemberEntity gm
                    WHERE gm.group.id = p.groupId AND gm.user.id = :actorId
                      AND gm.role = com.web.labportalbackend.research.enums.GroupRole.LEADER
                      AND gm.active = true AND gm.deleted = false
                  ))) OR (:managerAuthorized = true AND projectLab.manager.id = :actorId))
              AND (:projectId IS NULL OR p.projectId = :projectId)
              AND (:groupId IS NULL OR p.groupId = :groupId)
              AND (:status IS NULL OR p.status = :status)
            ORDER BY p.createdAt DESC, p.id DESC
            """,
            countQuery = """
            SELECT COUNT(p) FROM TaskProposalEntity p
            JOIN ProjectEntity project ON project.id = p.projectId
            JOIN GroupEntity grp ON grp.id = p.groupId
            JOIN project.lab projectLab JOIN grp.lab groupLab
            WHERE p.active = true AND p.deleted = false
              AND project.active = true AND project.deleted = false
              AND grp.active = true AND grp.deleted = false
              AND projectLab.active = true AND projectLab.deleted = false
              AND groupLab.active = true AND groupLab.deleted = false
              AND projectLab.id = groupLab.id
              AND (grp.project IS NULL OR grp.project.id = project.id)
              AND (project.group IS NULL OR project.group.id = grp.id)
              AND (grp.project.id = project.id OR project.group.id = grp.id)
              AND ((:studentAuthorized = true AND (p.proposedById = :actorId OR EXISTS (
                    SELECT gm.id FROM GroupMemberEntity gm
                    WHERE gm.group.id = p.groupId AND gm.user.id = :actorId
                      AND gm.role = com.web.labportalbackend.research.enums.GroupRole.LEADER
                      AND gm.active = true AND gm.deleted = false
                  ))) OR (:managerAuthorized = true AND projectLab.manager.id = :actorId))
              AND (:projectId IS NULL OR p.projectId = :projectId)
              AND (:groupId IS NULL OR p.groupId = :groupId)
              AND (:status IS NULL OR p.status = :status)
            """)
    Page<TaskProposalEntity> findVisibleForActor(@Param("actorId") Long actorId,
                                                   @Param("projectId") Long projectId,
                                                   @Param("groupId") Long groupId,
                                                   @Param("status") TaskProposalStatus status,
                                                   @Param("studentAuthorized") boolean studentAuthorized,
                                                   @Param("managerAuthorized") boolean managerAuthorized,
                                                   Pageable pageable);
}
