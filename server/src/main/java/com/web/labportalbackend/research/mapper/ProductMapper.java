package com.web.labportalbackend.research.mapper;

import com.web.labportalbackend.research.dto.response.ProductResponse;
import com.web.labportalbackend.research.entity.ProductEntity;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProductMapper {

    public static ProductResponse toResponse(ProductEntity product) {
        return ProductResponse.builder()
                .id(product.getId())
                .projectId(product.getProjectId())
                .name(product.getName())
                .fileUrl(product.getFileUrl())
                .createdAt(product.getCreatedAt())
                .build();
    }
}
