package com.web.labportalbackend.booking.repository;

import com.web.labportalbackend.booking.entity.ComplaintEntity;
import com.web.labportalbackend.common.enums.ComplaintStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ComplaintRepository extends JpaRepository<ComplaintEntity, Long> {
    List<ComplaintEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<ComplaintEntity> findByStatus(ComplaintStatus status);
}
