package com.web.labportalbackend.research.repository;

import com.web.labportalbackend.research.entity.ProductEntity;
import com.web.labportalbackend.research.enums.ProductType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<ProductEntity, Long> {
    List<ProductEntity> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    List<ProductEntity> findByProjectIdAndDeletedFalseAndActiveTrueOrderByCreatedAtDescVersionDesc(Long projectId);

    @Query("""
            SELECT MAX(p.version)
            FROM ProductEntity p
            WHERE p.projectId = :projectId
              AND p.groupId = :groupId
              AND p.productType = :productType
              AND p.active = true
              AND p.deleted = false
            """)
    Optional<Integer> findMaxVersionByProjectIdAndGroupIdAndProductType(
            @Param("projectId") Long projectId,
            @Param("groupId") Long groupId,
            @Param("productType") ProductType productType
    );

    @Query("""
            SELECT MAX(p.version)
            FROM ProductEntity p
            WHERE p.projectId = :projectId
              AND p.submittedById = :submittedById
              AND p.productType = :productType
              AND p.groupId IS NULL
              AND p.active = true
              AND p.deleted = false
            """)
    Optional<Integer> findMaxPersonalVersionByProjectIdAndSubmitterAndProductType(
            @Param("projectId") Long projectId,
            @Param("submittedById") Long submittedById,
            @Param("productType") ProductType productType
    );
}
