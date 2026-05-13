package com.web.labportalbackend.research.service;

import com.web.labportalbackend.research.dto.response.ProductResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ProductService {
    ProductResponse submitProduct(Long projectId, MultipartFile file, String name);

    List<ProductResponse> getByProject(Long projectId);
}
