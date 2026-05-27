package com.web.labportalbackend.lab.repository;

import com.web.labportalbackend.lab.entity.Membership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MembershipRepository extends JpaRepository<Membership, Long> {

    /**
     * Find a membership by user and laboratory.
     * Checks for non-deleted memberships only.
     */
    Optional<Membership> findByUserIdAndLaboratoryIdAndDeletedFalse(Long userId, Long labId);

    /**
     * Check if a user is a member of a laboratory.
     */
    boolean existsByUserIdAndLaboratoryIdAndDeletedFalse(Long userId, Long labId);

    /**
     * Check if a user is an active member of a laboratory.
     */
    boolean existsByUserIdAndLaboratoryIdAndActiveTrueAndDeletedFalse(Long userId, Long labId);

    /**
     * Find all members of a laboratory.
     */
    List<Membership> findByLaboratoryIdAndDeletedFalse(Long labId);

    /**
     * Find all laboratory memberships for a user.
     */
    List<Membership> findByUserIdAndDeletedFalse(Long userId);
}
