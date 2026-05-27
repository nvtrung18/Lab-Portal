package com.web.labportalbackend.booking.repository;

import com.web.labportalbackend.booking.entity.PenaltyEntity;
import com.web.labportalbackend.common.enums.PenaltyStatus;
import com.web.labportalbackend.common.enums.PenaltyType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PenaltyRepository extends JpaRepository<PenaltyEntity, Long> {
    List<PenaltyEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<PenaltyEntity> findByStatus(PenaltyStatus status);

    boolean existsByBookingId(Long bookingId);

    boolean existsByBookingIdAndTypeAndStatus(Long bookingId, PenaltyType type, PenaltyStatus status);

    @Query("SELECT p FROM PenaltyEntity p WHERE p.slot.id = :slotId AND p.deleted = false AND p.active = true ORDER BY p.createdAt DESC")
    List<PenaltyEntity> findActiveBySlotId(@Param("slotId") Long slotId);
}
