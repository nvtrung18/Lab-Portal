package com.web.labportalbackend.research.dto.response;

import com.web.labportalbackend.research.enums.ProjectStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class ProjectResponse {
    private Long id;
    private Long groupId;
    private String title;
    private ProjectStatus status;
    private LocalDate startDate;
    private LocalDate endDate;
}
