package com.web.labportalbackend.booking.repository;

import com.web.labportalbackend.booking.entity.CleaningEntity;
import com.web.labportalbackend.common.enums.CleaningStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CleaningRepository extends JpaRepository<CleaningEntity, Long> {
    List<CleaningEntity> findByStatus(CleaningStatus status);

    List<CleaningEntity> findByStaffIdAndStatus(Long staffId, CleaningStatus status);

    Optional<CleaningEntity> findBySlotId(Long slotId);

    boolean existsBySlotId(Long slotId);
}
