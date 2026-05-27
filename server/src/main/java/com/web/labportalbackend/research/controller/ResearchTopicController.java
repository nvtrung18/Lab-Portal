package com.web.labportalbackend.research.controller;

import com.web.labportalbackend.common.dto.Response;
import com.web.labportalbackend.research.dto.request.CreateTopicRequest;
import com.web.labportalbackend.research.dto.response.TopicResponse;
import com.web.labportalbackend.research.service.ResearchTopicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Research Topic", description = "Research topic management endpoints")
public class ResearchTopicController {

    private final ResearchTopicService topicService;

    @GetMapping("/labs/{labId}/research-topics")
    @Operation(summary = "Get research topics by lab")
    public ResponseEntity<Response<List<TopicResponse>>> getByLab(@PathVariable Long labId) {
        return ResponseEntity.ok(Response.ok("Research topics retrieved successfully", topicService.getByLab(labId)));
    }

    @PostMapping("/research-topics")
    @Operation(summary = "Create research topic")
    public ResponseEntity<Response<TopicResponse>> createTopic(@Valid @RequestBody CreateTopicRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.ok("Research topic created successfully", topicService.createTopic(request)));
    }
}
