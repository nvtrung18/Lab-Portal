package com.web.labportalbackend.booking.repository;

import com.web.labportalbackend.booking.entity.PenaltyEntity;
import com.web.labportalbackend.common.enums.PenaltyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PenaltyRepository extends JpaRepository<PenaltyEntity, Long> {
    List<PenaltyEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<PenaltyEntity> findByStatus(PenaltyStatus status);

    boolean existsByBookingId(Long bookingId);
}
