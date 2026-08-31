package com.web.labportalbackend.face.repository;

import com.web.labportalbackend.face.entity.FaceCheckinLogEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface FaceCheckinLogRepository extends JpaRepository<FaceCheckinLogEntity, Long>,
        JpaSpecificationExecutor<FaceCheckinLogEntity> {
    List<FaceCheckinLogEntity> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);
    List<FaceCheckinLogEntity> findByLabIdOrderByCreatedAtDescIdDesc(Long labId);
    Optional<FaceCheckinLogEntity> findFirstByBookingIdOrderByCreatedAtDescIdDesc(Long bookingId);
}
