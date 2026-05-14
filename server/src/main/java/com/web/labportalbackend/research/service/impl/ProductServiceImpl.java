package com.web.labportalbackend.research.service.impl;

import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.research.dto.response.ProductResponse;
import com.web.labportalbackend.research.entity.ProductEntity;
import com.web.labportalbackend.research.mapper.ProductMapper;
import com.web.labportalbackend.research.repository.ProductRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.service.LogService;
import com.web.labportalbackend.research.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProjectRepository projectRepository;
    private final LogService logService;

    @Override
    @Transactional
    public ProductResponse submitProduct(Long projectId, MultipartFile file, String name) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Project", projectId);
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Product file is required");
        }

        String productName = StringUtils.hasText(name) ? name.trim() : file.getOriginalFilename();
        if (!StringUtils.hasText(productName)) {
            productName = "Research Product";
        }

        ProductEntity product = ProductEntity.builder()
                .projectId(projectId)
                .name(productName)
                .fileUrl(generateFileUrl(projectId, file))
                .build();

        ProductEntity saved = productRepository.save(product);
        logService.logAction(projectId, null, "SUBMIT_PRODUCT", "Submitted product: " + productName);
        return ProductMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponse> getByProject(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Project", projectId);
        }
        return productRepository.findByProjectIdOrderByCreatedAtDesc(projectId)
                .stream()
                .map(ProductMapper::toResponse)
                .toList();
    }

    private String generateFileUrl(Long projectId, MultipartFile file) {
        String filename = StringUtils.cleanPath(file.getOriginalFilename() == null ? "product.bin" : file.getOriginalFilename());
        return "local://research-products/" + projectId + "/" + Instant.now().toEpochMilli() + "-" + filename;
    }
}
