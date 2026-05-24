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
    private Long topicId;
    private String code;
    private String title;
    private String description;
    private String objective;
    private ProjectStatus status;
    private String createdByName;
    private String managerName;
    private com.web.labportalbackend.research.enums.ResearchPriority priority;
    private String requiredProducts;
    private String evaluationCriteria;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate expectedEndDate;
    private java.time.Instant createdAt;
}
