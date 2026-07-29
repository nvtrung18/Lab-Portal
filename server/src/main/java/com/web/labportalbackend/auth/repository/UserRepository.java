package com.web.labportalbackend.auth.repository;

import com.web.labportalbackend.auth.entity.User;
import com.web.labportalbackend.common.enums.UserStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @Lock(LockModeType.PESSIMISTIC_READ)
    @EntityGraph(attributePaths = "roles")
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForStatusAuthorization(@Param("id") Long id);

    Optional<User> findByEmail(String email);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmailOrUsername(String email, String username);

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    List<User> findByStatus(UserStatus status);

    /**
     * Find all users that hold a specific role name.
     */
    @Query("SELECT u FROM User u JOIN u.roles r WHERE r.name = :roleName")
    List<User> findByRoleName(@Param("roleName") String roleName);

    @Query("SELECT COUNT(u) FROM User u WHERE u.deleted = false")
    long countRegisteredUsers();

    @Query("SELECT COUNT(u) FROM User u WHERE u.status = :status AND u.deleted = false")
    long countByStatusAndNotDeleted(@Param("status") UserStatus status);

    @Query("""
            SELECT COUNT(DISTINCT u)
            FROM User u
            JOIN u.roles r
            WHERE r.name = :roleName
              AND u.deleted = false
            """)
    long countByRoleNameAndNotDeleted(@Param("roleName") String roleName);

    @Query("""
            SELECT COUNT(DISTINCT u)
            FROM User u
            JOIN u.roles r
            WHERE r.name = 'LAB_MANAGER'
              AND u.deleted = false
              AND NOT EXISTS (
                    SELECT l.id
                    FROM Laboratory l
                    WHERE l.manager.id = u.id
                      AND l.deleted = false
              )
            """)
    long countUnassignedManagers();
}
