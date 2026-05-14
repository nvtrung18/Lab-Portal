package com.web.labportalbackend.lab.mapper;

import com.web.labportalbackend.auth.dto.UserResponse;
import com.web.labportalbackend.lab.dto.request.CreateLabRequest;
import com.web.labportalbackend.lab.dto.response.LabResponse;
import com.web.labportalbackend.lab.entity.Laboratory;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import java.util.stream.Collectors;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class LabMapper {
    public static Laboratory toEntity(CreateLabRequest request) {
        Laboratory lab = new Laboratory();
        lab.setLabName(request.getLabName());
        lab.setDescription(request.getDescription());
        lab.setLocation(request.getLocation());
        lab.setCapacity(request.getCapacity());
        lab.setDepartment(request.getDepartment());
        return lab;
    }

    public static LabResponse toResponse(Laboratory lab) {
        UserResponse managerDTO = null;
        if (lab.getManager() != null) {
            managerDTO = UserResponse.builder()
                    .id(lab.getManager().getId())
                    .email(lab.getManager().getEmail())
                    .username(lab.getManager().getUsername())
                    .fullName(lab.getManager().getFullName())
                    .phone(lab.getManager().getPhone())
                    .status(lab.getManager().getStatus().toString())
                    .roles(lab.getManager().getRoles().stream()
                            .map(r -> r.getName())
                            .collect(Collectors.toSet()))
                    .createdAt(lab.getManager().getCreatedAt())
                    .updatedAt(lab.getManager().getUpdatedAt())
                    .build();
        }
        return LabResponse.builder()
                .id(lab.getId()).labName(lab.getLabName()).description(lab.getDescription())
                .location(lab.getLocation()).capacity(lab.getCapacity()).department(lab.getDepartment())
                .status(lab.getStatus()).manager(managerDTO)
                .createdAt(lab.getCreatedAt()).updatedAt(lab.getUpdatedAt())
                .build();
    }
}
