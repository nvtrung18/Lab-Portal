package com.web.labportalbackend.lab.repository;

import com.web.labportalbackend.common.enums.LabStatus;
import com.web.labportalbackend.lab.entity.Laboratory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LaboratoryRepository extends JpaRepository<Laboratory, Long> {

    Optional<Laboratory> findByLabName(String labName);

    List<Laboratory> findByDepartment(String department);

    List<Laboratory> findByStatus(LabStatus status);

    boolean existsByLabName(String labName);
}
