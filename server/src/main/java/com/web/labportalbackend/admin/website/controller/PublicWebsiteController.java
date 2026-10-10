package com.web.labportalbackend.admin.website.controller;

import com.web.labportalbackend.admin.website.dto.PublishedWebsiteContent;
import com.web.labportalbackend.admin.website.service.WebsiteContentService;
import com.web.labportalbackend.common.dto.Response;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PublicWebsiteController {
    private final WebsiteContentService service;

    @GetMapping("/public/website")
    @Operation(summary = "Read visible published website content without authentication")
    public ResponseEntity<Response<PublishedWebsiteContent>> get() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Response.ok("Published website content retrieved", service.getPublished()));
    }
}
