package com.web.labportalbackend.auth.repository;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.common.enums.UserRole;
import com.web.labportalbackend.common.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    List<User> findByRole(UserRole role);

    List<User> findByStatus(UserStatus status);
}
