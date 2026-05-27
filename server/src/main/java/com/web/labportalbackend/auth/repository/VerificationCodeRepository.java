package com.web.labportalbackend.auth.repository;

import com.web.labportalbackend.auth.entity.VerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VerificationCodeRepository extends JpaRepository<VerificationCode, Long> {
    Optional<VerificationCode> findFirstByEmailAndTypeAndUsedFalseAndDeletedFalseOrderByCreatedAtDesc(
            String email,
            String type
    );
}
