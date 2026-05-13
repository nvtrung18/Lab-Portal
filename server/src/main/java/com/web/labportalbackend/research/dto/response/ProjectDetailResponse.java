package com.web.labportalbackend.research.dto.response;

import com.web.labportalbackend.research.enums.ProjectStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Builder
public class ProjectDetailResponse {
    private Long id;
    private Long groupId;
    private String title;
    private String description;
    private ProjectStatus status;
    private LocalDate startDate;
    private LocalDate endDate;
    private Instant createdAt;
    private Instant updatedAt;
}
