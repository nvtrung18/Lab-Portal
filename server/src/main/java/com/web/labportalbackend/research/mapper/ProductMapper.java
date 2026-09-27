package com.web.labportalbackend.research.mapper;

import com.web.labportalbackend.research.dto.response.ProductResponse;
import com.web.labportalbackend.research.entity.ProductEntity;
import com.web.labportalbackend.auth.entity.User;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProductMapper {

    public static ProductResponse toResponse(ProductEntity product, User submitter) {
        return ProductResponse.builder()
                .id(product.getId())
                .projectId(product.getProjectId())
                .groupId(product.getGroupId())
                .submittedById(product.getSubmittedById())
                .submittedByName(submitter == null ? null : submitter.getFullName())
                .submittedByEmail(submitter == null ? null : submitter.getEmail())
                .productType(product.getProductType())
                .title(product.getTitle())
                .description(product.getDescription())
                .name(product.getName())
                .fileUrl(product.getFileUrl())
                .fileName(product.getFileName())
                .fileType(product.getFileType())
                .fileSize(product.getFileSize())
                .externalLink(product.getExternalLink())
                .version(product.getVersion())
                .status(product.getStatus())
                .submittedAt(product.getSubmittedAt())
                .createdAt(product.getCreatedAt())
                .build();
    }
}
