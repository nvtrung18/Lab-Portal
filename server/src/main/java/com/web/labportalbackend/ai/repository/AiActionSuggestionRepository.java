package com.web.labportalbackend.ai.repository;

import com.web.labportalbackend.ai.entity.AiActionSuggestionEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AiActionSuggestionRepository extends JpaRepository<AiActionSuggestionEntity, Long>,
        JpaSpecificationExecutor<AiActionSuggestionEntity> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select suggestion from AiActionSuggestionEntity suggestion where suggestion.id = :id "
            + "and suggestion.active = true and suggestion.deleted = false")
    Optional<AiActionSuggestionEntity> findByIdForUpdate(@Param("id") Long id);
}
