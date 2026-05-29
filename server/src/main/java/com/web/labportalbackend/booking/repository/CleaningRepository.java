package com.web.labportalbackend.booking.repository;

import com.web.labportalbackend.booking.entity.CleaningEntity;
import com.web.labportalbackend.common.enums.CleaningStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CleaningRepository extends JpaRepository<CleaningEntity, Long> {
    List<CleaningEntity> findByStatus(CleaningStatus status);

    List<CleaningEntity> findByStaffIdAndStatus(Long staffId, CleaningStatus status);

    Optional<CleaningEntity> findBySlotId(Long slotId);

    Optional<CleaningEntity> findFirstBySlotId(Long slotId);

    Optional<CleaningEntity> findFirstBySlotIdAndStaffIsNullAndStatus(Long slotId, CleaningStatus status);

    boolean existsBySlotId(Long slotId);

    @Query("SELECT c FROM CleaningEntity c WHERE c.slot.lab.id = :labId AND c.deleted = false AND c.active = true ORDER BY c.slot.startTime DESC, c.createdAt DESC")
    List<CleaningEntity> findActiveByLabId(@Param("labId") Long labId);

    @Query("SELECT c FROM CleaningEntity c WHERE c.slot.id IN :slotIds AND c.deleted = false AND c.active = true ORDER BY c.slot.startTime ASC, c.createdAt ASC")
    List<CleaningEntity> findActiveBySlotIdIn(@Param("slotIds") List<Long> slotIds);

    @Query("SELECT c FROM CleaningEntity c WHERE c.staff.id = :staffId AND c.deleted = false AND c.active = true ORDER BY c.slot.startTime DESC")
    List<CleaningEntity> findActiveByStaffId(@Param("staffId") Long staffId);

    @Query("SELECT COUNT(c) > 0 FROM CleaningEntity c WHERE c.slot.id = :slotId " +
           "AND c.staff.id = :staffId " +
           "AND c.status <> 'CANCELLED' " +
           "AND c.deleted = false AND c.active = true")
    boolean existsActiveNonCancelledBySlotIdAndStaffId(
            @Param("slotId") Long slotId,
            @Param("staffId") Long staffId
    );

    @Query("""
            SELECT COUNT(c)
            FROM CleaningEntity c
            WHERE c.status NOT IN :completedStatuses
              AND c.deleted = false
              AND c.active = true
            """)
    long countActiveIncomplete(@Param("completedStatuses") List<CleaningStatus> completedStatuses);

    @Query("""
            SELECT COUNT(c)
            FROM CleaningEntity c
            WHERE c.slot.lab.id = :labId
              AND c.status NOT IN :completedStatuses
              AND c.deleted = false
              AND c.active = true
            """)
    long countActiveIncompleteByLabId(
            @Param("labId") Long labId,
            @Param("completedStatuses") List<CleaningStatus> completedStatuses
    );
}

