package com.web.labportalbackend.research.dto.response;

import com.web.labportalbackend.research.enums.TopicStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class TopicResponse {
    private Long id;
    private Long labId;
    private String name;
    private String description;
    private String requirements;
    private String references;
    private String managerName;
    private String createdByName;
    private TopicStatus status;
    private Long groupCount;
    private Instant createdAt;
    private Instant updatedAt;
}
