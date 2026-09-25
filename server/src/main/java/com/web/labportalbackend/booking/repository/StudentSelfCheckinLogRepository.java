package com.web.labportalbackend.booking.repository;

import com.web.labportalbackend.booking.entity.StudentSelfCheckinLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentSelfCheckinLogRepository extends JpaRepository<StudentSelfCheckinLogEntity, Long> {
}
