package com.web.labportalbackend.lab.repository;

import com.web.labportalbackend.common.enums.LabStatus;
import com.web.labportalbackend.lab.entity.Laboratory;
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
public interface LaboratoryRepository extends JpaRepository<Laboratory, Long> {

    @Lock(LockModeType.PESSIMISTIC_READ)
    @EntityGraph(type = EntityGraph.EntityGraphType.FETCH)
    @Query("""
            SELECT l
            FROM Laboratory l
            WHERE l.id = :id
              AND l.deleted = false
              AND l.active = true
            """)
    Optional<Laboratory> findByIdForStatusAuthorization(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @EntityGraph(type = EntityGraph.EntityGraphType.FETCH)
    @Query("""
            SELECT l
            FROM Laboratory l
            WHERE l.id = :id
              AND l.manager.id = :managerId
              AND l.deleted = false
              AND l.active = true
            """)
    Optional<Laboratory> findManagedByIdForStatusAuthorization(
            @Param("id") Long id,
            @Param("managerId") Long managerId
    );

    Optional<Laboratory> findByLabName(String labName);

    Optional<Laboratory> findFirstByManagerIdAndDeletedFalse(Long managerId);

    boolean existsByIdAndManagerIdAndActiveTrueAndDeletedFalse(Long id, Long managerId);

    List<Laboratory> findByDepartment(String department);

    List<Laboratory> findByStatus(LabStatus status);

    boolean existsByLabName(String labName);

    @Query("SELECT COUNT(l) FROM Laboratory l WHERE l.deleted = false")
    long countNotDeleted();

    @Query("SELECT COUNT(l) FROM Laboratory l WHERE l.status = :status AND l.deleted = false")
    long countByStatusAndNotDeleted(@Param("status") LabStatus status);

    @Query("SELECT COUNT(l) FROM Laboratory l WHERE l.manager IS NULL AND l.deleted = false")
    long countWithoutManager();
}
