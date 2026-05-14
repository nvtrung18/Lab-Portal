package com.web.labportalbackend.research.repository;

import com.web.labportalbackend.research.entity.ProductEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductRepository extends JpaRepository<ProductEntity, Long> {
    List<ProductEntity> findByProjectIdOrderByCreatedAtDesc(Long projectId);
}
