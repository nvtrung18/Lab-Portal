package com.web.labportalbackend.research.service;

import com.web.labportalbackend.research.dto.response.ProductResponse;
import com.web.labportalbackend.research.entity.ProductEntity;
import com.web.labportalbackend.research.repository.ProductRepository;
import com.web.labportalbackend.research.repository.ProjectRepository;
import com.web.labportalbackend.research.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private LogService logService;

    @InjectMocks
    private ProductServiceImpl productService;

    @Test
    void submitProduct_uploadsFileAndCreatesProduct() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "final.zip",
                "application/zip",
                "artifact".getBytes()
        );

        when(projectRepository.existsById(10L)).thenReturn(true);
        when(productRepository.save(any(ProductEntity.class))).thenAnswer(invocation -> {
            ProductEntity product = invocation.getArgument(0);
            product.setId(100L);
            product.setCreatedAt(Instant.parse("2026-05-14T03:00:00Z"));
            return product;
        });

        ProductResponse response = productService.submitProduct(10L, file, "Final Product");

        assertEquals(100L, response.getId());
        assertEquals(10L, response.getProjectId());
        assertEquals("Final Product", response.getName());
        assertTrue(response.getFileUrl().startsWith("local://research-products/10/"));
        assertTrue(response.getFileUrl().endsWith("-final.zip"));

        ArgumentCaptor<ProductEntity> captor = ArgumentCaptor.forClass(ProductEntity.class);
        verify(productRepository).save(captor.capture());
        assertEquals(10L, captor.getValue().getProjectId());
        assertEquals("Final Product", captor.getValue().getName());
        verify(logService).logAction(10L, null, "SUBMIT_PRODUCT", "Submitted product: Final Product");
    }
}
