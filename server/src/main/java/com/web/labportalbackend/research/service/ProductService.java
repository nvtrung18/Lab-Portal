package com.web.labportalbackend.research.service;

import com.web.labportalbackend.research.dto.request.SubmitProductRequest;
import com.web.labportalbackend.research.dto.response.ProductResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ProductService {
    ProductResponse submitProduct(SubmitProductRequest request, MultipartFile file);

    List<ProductResponse> getByProject(Long projectId);
}
