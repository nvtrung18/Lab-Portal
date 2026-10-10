package com.web.labportalbackend.admin.website.controller;

import com.web.labportalbackend.admin.website.dto.WebsiteSaveRequest;
import com.web.labportalbackend.admin.website.dto.WebsiteState;
import com.web.labportalbackend.admin.website.dto.WebsiteImageResponse;
import com.web.labportalbackend.admin.website.dto.WebsiteMemberSource;
import com.web.labportalbackend.admin.website.service.WebsiteContentService;
import com.web.labportalbackend.admin.website.service.WebsiteImageService;
import com.web.labportalbackend.common.dto.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/website")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Website", description = "Public laboratory website editorial content")
public class WebsiteContentController {
    private final WebsiteContentService service;
    private final WebsiteImageService imageService;

    @GetMapping("/member-sources")
    @Operation(summary = "List non-deleted accounts for public member profile selection")
    public ResponseEntity<Response<List<WebsiteMemberSource>>> memberSources() {
        return ResponseEntity.ok(Response.ok("Website member sources retrieved", service.getMemberSources()));
    }

    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a PNG, JPEG or WebP website image up to 5 MB")
    public ResponseEntity<Response<WebsiteImageResponse>> upload(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(Response.ok("Website image uploaded", imageService.upload(file)));
    }

    @GetMapping
    @Operation(summary = "Get the saved draft and publication state")
    public ResponseEntity<Response<WebsiteState>> get() {
        return ResponseEntity.ok(Response.ok("Website content retrieved", service.getState()));
    }

    @PutMapping("/draft")
    @Operation(summary = "Save a draft without changing the published website")
    public ResponseEntity<Response<WebsiteState>> save(@Valid @RequestBody WebsiteSaveRequest request) {
        return ResponseEntity.ok(Response.ok("Website draft saved", service.saveDraft(request)));
    }

    @PostMapping("/publish")
    @Operation(summary = "Save and publish the submitted website content")
    public ResponseEntity<Response<WebsiteState>> publish(@Valid @RequestBody WebsiteSaveRequest request) {
        return ResponseEntity.ok(Response.ok("Website content published", service.publish(request)));
    }
}
