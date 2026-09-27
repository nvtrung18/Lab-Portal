package com.web.labportalbackend.research.controller;

import com.web.labportalbackend.common.dto.Response;
import com.web.labportalbackend.research.dto.request.SubmitProductRequest;
import com.web.labportalbackend.research.dto.response.ProductResponse;
import com.web.labportalbackend.research.dto.response.ProductFileDownload;
import com.web.labportalbackend.research.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.nio.charset.StandardCharsets;

@RestController
@RequiredArgsConstructor
@Tag(name = "Research Product", description = "Final project product submission endpoints")
public class ProductController {

    private final ProductService productService;

    @PostMapping(value = "/products", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Submit research product", description = "Upload or link a versioned research product")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Response<ProductResponse>> submitProduct(
            @Valid @ModelAttribute SubmitProductRequest request,
            @RequestParam(value = "file", required = false) MultipartFile file
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.ok("Đã nộp sản phẩm nghiên cứu.", productService.submitProduct(request, file)));
    }

    @GetMapping("/projects/{projectId}/products")
    @Operation(summary = "Get project products", description = "Retrieve final product submissions for a project")
    @PreAuthorize("hasAnyRole('LAB_MANAGER', 'STUDENT')")
    public ResponseEntity<Response<List<ProductResponse>>> getProductsByProject(@PathVariable Long projectId) {
        return ResponseEntity.ok(
                Response.ok("Products retrieved successfully", productService.getByProject(projectId))
        );
    }

    @GetMapping("/research-groups/{groupId}/products")
    @Operation(summary = "Get products by research group")
    @PreAuthorize("hasAnyRole('LAB_MANAGER', 'STUDENT')")
    public ResponseEntity<Response<List<ProductResponse>>> getProductsByGroup(@PathVariable Long groupId) {
        return ResponseEntity.ok(
                Response.ok("Products retrieved successfully", productService.getByGroup(groupId))
        );
    }

    @GetMapping("/products/{id}/file")
    @Operation(summary = "Download an authorized research product attachment")
    @PreAuthorize("hasAnyRole('LAB_MANAGER', 'STUDENT')")
    public ResponseEntity<Resource> downloadProductFile(@PathVariable Long id) {
        ProductFileDownload file = productService.downloadProductFile(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(file.resource());
    }
}
