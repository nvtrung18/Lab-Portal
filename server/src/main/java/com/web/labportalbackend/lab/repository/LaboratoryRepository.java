package com.web.labportalbackend.lab.repository;

import com.web.labportalbackend.common.enums.LabStatus;
import com.web.labportalbackend.lab.entity.Laboratory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LaboratoryRepository extends JpaRepository<Laboratory, Long> {

    Optional<Laboratory> findByLabName(String labName);

    Optional<Laboratory> findFirstByManagerIdAndDeletedFalse(Long managerId);

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
