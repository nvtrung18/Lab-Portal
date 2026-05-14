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
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Research Group", description = "Research group and member management endpoints")
public class GroupController {

    private final GroupService groupService;

    @PostMapping("/groups")
    @Operation(summary = "Create research group")
    public ResponseEntity<Response<GroupResponse>> createGroup(
            @Valid @RequestBody CreateGroupRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.ok("Research group created successfully", groupService.createGroup(request)));
    }

    @PostMapping("/groups/{id}/members")
    @Operation(summary = "Add member to research group")
    public ResponseEntity<Response<GroupMemberResponse>> addMember(
            @PathVariable Long id,
            @Valid @RequestBody AddMemberRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Response.ok("Group member added successfully", groupService.addMember(id, request)));
    }

    @GetMapping("/labs/{id}/groups")
    @Operation(summary = "Get research groups by lab")
    public ResponseEntity<Response<List<GroupResponse>>> getByLab(@PathVariable Long id) {
        return ResponseEntity.ok(
                Response.ok("Research groups retrieved successfully", groupService.getByLab(id))
        );
    }
}
