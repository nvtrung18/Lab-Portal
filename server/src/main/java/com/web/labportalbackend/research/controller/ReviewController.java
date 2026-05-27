package com.web.labportalbackend.research.controller;

import com.web.labportalbackend.auth.repository.UserRepository;
import com.web.labportalbackend.common.dto.Response;
import com.web.labportalbackend.common.exception.ResourceNotFoundException;
import com.web.labportalbackend.research.dto.request.CreateCommentRequest;
import com.web.labportalbackend.research.dto.response.CommentResponse;
import com.web.labportalbackend.research.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Research Review", description = "Report review comment endpoints")
public class ReviewController {

    private final ReviewService reviewService;
    private final UserRepository userRepository;

    @PostMapping("/reports/{id}/comments")
    @Operation(summary = "Add comment to report")
    @PreAuthorize("hasAnyRole('LAB_MANAGER', 'STUDENT')")
    public ResponseEntity<Response<CommentResponse>> addComment(
            @PathVariable Long id,
            @Valid @RequestBody CreateCommentRequest request,
            Authentication authentication
    ) {
        Long authorId = resolveAuthorId(authentication);
        CommentResponse comment = reviewService.addComment(id, authorId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.ok("Comment created successfully", comment));
    }

    @GetMapping("/reports/{id}/comments")
    @Operation(summary = "Get comments by report")
    @PreAuthorize("hasAnyRole('LAB_MANAGER', 'STUDENT')")
    public ResponseEntity<Response<List<CommentResponse>>> getCommentsByReport(@PathVariable Long id) {
        return ResponseEntity.ok(
                Response.ok("Comments retrieved successfully", reviewService.getByReport(id))
        );
    }

    private Long resolveAuthorId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new ResourceNotFoundException("User", "authenticated principal", null);
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("User", "username", authentication.getName()))
                .getId();
    }
}
