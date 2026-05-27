package com.web.labportalbackend.lab.repository;

import com.web.labportalbackend.common.enums.ApplicationStatus;
import com.web.labportalbackend.lab.entity.Application;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, Long> {

    /**
     * Find if a user has already applied to a specific laboratory.
     * Checks for non-deleted applications only.
     */
    Optional<Application> findByUserIdAndLaboratoryIdAndDeletedFalse(Long userId, Long labId);

    /**
     * Find the latest application for a user and laboratory.
     */
    Optional<Application> findTopByUserIdAndLaboratoryIdAndDeletedFalseOrderByCreatedAtDesc(Long userId, Long labId);

    /**
     * Check if user has existing (non-deleted) application for a lab.
     */
    boolean existsByUserIdAndLaboratoryIdAndDeletedFalse(Long userId, Long labId);

    /**
     * Check if user has an application with a specific status for a lab.
     */
    boolean existsByUserIdAndLaboratoryIdAndStatusAndDeletedFalse(
            Long userId,
            Long labId,
            ApplicationStatus status);

    /**
     * Find all applications for a specific laboratory.
     */
    List<Application> findByLaboratoryIdAndDeletedFalse(Long labId);

    /**
     * Find all applications by a specific user.
     */
    List<Application> findByUserIdAndDeletedFalse(Long userId);

    /**
     * Find applications by status with pagination.
     */
    Page<Application> findByStatusAndDeletedFalse(ApplicationStatus status, Pageable pageable);

    /**
     * Find all non-deleted applications with pagination.
     */
    Page<Application> findByDeletedFalse(Pageable pageable);
}
