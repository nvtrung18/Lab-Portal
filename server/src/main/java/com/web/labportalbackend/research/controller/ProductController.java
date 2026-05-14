package com.web.labportalbackend.research.controller;

import com.web.labportalbackend.common.dto.Response;
import com.web.labportalbackend.research.dto.response.ProductResponse;
import com.web.labportalbackend.research.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Research Product", description = "Final project product submission endpoints")
public class ProductController {

    private final ProductService productService;

    @PostMapping(value = "/products", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Submit final project product", description = "Upload a final product file for a research project")
    public ResponseEntity<Response<ProductResponse>> submitProduct(
            @Parameter(description = "Research project ID", example = "1")
            @RequestParam("projectId") Long projectId,
            @Parameter(description = "Optional product display name", example = "Final Demo Package")
            @RequestParam(value = "name", required = false) String name,
            @Parameter(description = "Final product file")
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.ok("Product submitted successfully", productService.submitProduct(projectId, file, name)));
    }

    @GetMapping("/projects/{id}/products")
    @Operation(summary = "Get project products", description = "Retrieve final product submissions for a project")
    public ResponseEntity<Response<List<ProductResponse>>> getProductsByProject(@PathVariable Long id) {
        return ResponseEntity.ok(
                Response.ok("Products retrieved successfully", productService.getByProject(id))
        );
    }
}
