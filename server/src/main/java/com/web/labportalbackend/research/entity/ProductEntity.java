package com.web.labportalbackend.research.entity;

import com.web.labportalbackend.common.entity.BaseEntity;
import com.web.labportalbackend.research.enums.ProductStatus;
import com.web.labportalbackend.research.enums.ProductType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "products", indexes = {
        @Index(name = "idx_products_project_id", columnList = "project_id"),
        @Index(name = "idx_products_group_id", columnList = "group_id"),
        @Index(name = "idx_products_submitter_id", columnList = "submitted_by_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductEntity extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "group_id")
    private Long groupId;

    @Column(name = "submitted_by_id", nullable = false)
    private Long submittedById;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 30)
    private ProductType productType;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 200)
    private String name;

    @Column(name = "file_url", length = 500)
    private String fileUrl;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "file_type", length = 100)
    private String fileType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "external_link", length = 1000)
    private String externalLink;

    @Column(nullable = false)
    private Integer version;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProductStatus status = ProductStatus.SUBMITTED;

    @Builder.Default
    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt = Instant.now();
}
