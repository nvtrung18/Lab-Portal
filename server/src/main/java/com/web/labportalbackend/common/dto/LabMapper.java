package com.web.labportalbackend.common.dto;

import com.web.labportalbackend.auth.dto.UserResponse;
import com.web.labportalbackend.lab.entity.Laboratory;

/**
 * Mapper for converting Laboratory entities to DTOs and vice versa.
 */
public class LabMapper {

    private LabMapper() {
        // Utility class, no instantiation
    }

    /**
     * Convert CreateLabRequest to Laboratory entity.
     */
    public static Laboratory toEntity(CreateLabRequest request) {
        Laboratory laboratory = new Laboratory();
        laboratory.setLabName(request.getLabName());
        laboratory.setDescription(request.getDescription());
        laboratory.setLocation(request.getLocation());
        laboratory.setCapacity(request.getCapacity());
        laboratory.setDepartment(request.getDepartment());
        return laboratory;
    }

    /**
     * Convert Laboratory entity to LabDTO.
     */
    public static LabDTO toDTO(Laboratory laboratory) {
        UserResponse managerDTO = null;
        if (laboratory.getManager() != null) {
            managerDTO = UserResponse.builder()
                    .id(laboratory.getManager().getId())
                    .email(laboratory.getManager().getEmail())
                    .username(laboratory.getManager().getUsername())
                    .fullName(laboratory.getManager().getFullName())
                    .phone(laboratory.getManager().getPhone())
                    .status(laboratory.getManager().getStatus().toString())
                    .roles(laboratory.getManager().getRoles().stream()
                            .map(r -> r.getName())
                            .collect(java.util.stream.Collectors.toSet()))
                    .createdAt(laboratory.getManager().getCreatedAt())
                    .updatedAt(laboratory.getManager().getUpdatedAt())
                    .build();
        }

        return LabDTO.builder()
                .id(laboratory.getId())
                .labName(laboratory.getLabName())
                .description(laboratory.getDescription())
                .location(laboratory.getLocation())
                .capacity(laboratory.getCapacity())
                .department(laboratory.getDepartment())
                .status(laboratory.getStatus())
                .manager(managerDTO)
                .createdAt(laboratory.getCreatedAt())
                .updatedAt(laboratory.getUpdatedAt())
                .build();
    }
}
