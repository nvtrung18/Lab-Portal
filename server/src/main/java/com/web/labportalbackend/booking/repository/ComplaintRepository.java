package com.web.labportalbackend.booking.repository;

import com.web.labportalbackend.booking.entity.ComplaintEntity;
import com.web.labportalbackend.common.enums.ComplaintStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ComplaintRepository extends JpaRepository<ComplaintEntity, Long> {
    List<ComplaintEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<ComplaintEntity> findByStatus(ComplaintStatus status);

    boolean existsByPenaltyId(Long penaltyId);

    boolean existsByPenaltyIdAndStatus(Long penaltyId, ComplaintStatus status);

    @Query("SELECT c FROM ComplaintEntity c WHERE c.penalty.booking.lab.id = :labId AND c.deleted = false AND c.active = true ORDER BY c.createdAt DESC")
    List<ComplaintEntity> findActiveByLabId(@Param("labId") Long labId);
}
