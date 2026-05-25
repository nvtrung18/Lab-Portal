package com.web.labportalbackend.research.controller;

import com.web.labportalbackend.common.dto.Response;
import com.web.labportalbackend.research.dto.request.AddMemberRequest;
import com.web.labportalbackend.research.dto.request.CreateGroupRequest;
import com.web.labportalbackend.research.dto.response.GroupMemberResponse;
import com.web.labportalbackend.research.dto.response.GroupResponse;
import com.web.labportalbackend.research.service.GroupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Research Group", description = "Research group and member management endpoints")
public class GroupController {

    private final GroupService groupService;

    @PostMapping("/groups")
    @Operation(summary = "Create research group")
    @PreAuthorize("hasRole('LAB_MANAGER')")
    public ResponseEntity<Response<GroupResponse>> createGroup(
            @Valid @RequestBody CreateGroupRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.ok("Research group created successfully", groupService.createGroup(request)));
    }

    @PostMapping("/research-groups")
    @Operation(summary = "Create research group for research project")
    @PreAuthorize("hasRole('LAB_MANAGER')")
    public ResponseEntity<Response<GroupResponse>> createResearchGroup(
            @Valid @RequestBody CreateGroupRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.ok("Research group created successfully", groupService.createGroup(request)));
    }

    @PutMapping("/research-groups/{id}")
    @Operation(summary = "Update research group")
    @PreAuthorize("hasRole('LAB_MANAGER')")
    public ResponseEntity<Response<GroupResponse>> updateResearchGroup(
            @PathVariable Long id,
            @Valid @RequestBody CreateGroupRequest request
    ) {
        return ResponseEntity.ok(
                Response.ok("Research group updated successfully", groupService.updateResearchGroup(id, request))
        );
    }

    @PostMapping("/groups/{id}/members")
    @Operation(summary = "Add member to research group")
    @PreAuthorize("hasRole('LAB_MANAGER')")
    public ResponseEntity<Response<GroupMemberResponse>> addMember(
            @PathVariable Long id,
            @Valid @RequestBody AddMemberRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.ok("Group member added successfully", groupService.addMember(id, request)));
    }

    @GetMapping("/labs/{id}/research-groups/me")
    @Operation(summary = "Get my research groups by laboratory")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Response<List<GroupResponse>>> getMyGroupsByLab(@PathVariable Long id) {
        return ResponseEntity.ok(
                Response.ok("My laboratory research groups retrieved successfully", groupService.getMyGroupsByLab(id))
        );
    }

    @GetMapping("/labs/{id}/groups")
    @Operation(summary = "Get research groups by lab")
    @PreAuthorize("hasRole('LAB_MANAGER')")
    public ResponseEntity<Response<List<GroupResponse>>> getByLab(@PathVariable Long id) {
        return ResponseEntity.ok(
                Response.ok("Research groups retrieved successfully", groupService.getByLab(id))
        );
    }

    @GetMapping("/research-topics/{id}/groups")
    @Operation(summary = "Get research groups by topic")
    @PreAuthorize("hasRole('LAB_MANAGER')")
    public ResponseEntity<Response<List<GroupResponse>>> getByTopic(@PathVariable Long id) {
        return ResponseEntity.ok(
                Response.ok("Research groups retrieved successfully", groupService.getByTopic(id))
        );
    }

    @GetMapping("/research-projects/{id}/groups")
    @Operation(summary = "Get research groups by research project")
    @PreAuthorize("hasAnyRole('LAB_MANAGER', 'STUDENT')")
    public ResponseEntity<Response<List<GroupResponse>>> getByProject(@PathVariable Long id) {
        return ResponseEntity.ok(
                Response.ok("Research groups retrieved successfully", groupService.getByProject(id))
        );
    }

    @GetMapping("/research-groups/{id}")
    @Operation(summary = "Get research group detail")
    @PreAuthorize("hasAnyRole('LAB_MANAGER', 'STUDENT')")
    public ResponseEntity<Response<GroupResponse>> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(
                Response.ok("Research group detail retrieved successfully", groupService.getDetail(id))
        );
    }
}
