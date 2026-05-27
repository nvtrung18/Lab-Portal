package com.web.labportalbackend.research.dto.request;

import com.web.labportalbackend.research.enums.TopicStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateTopicRequest {

    @NotNull(message = "Lab ID is required")
    private Long labId;

    @NotBlank(message = "Topic name is required")
    @Size(min = 3, max = 200, message = "Topic name must be between 3 and 200 characters")
    private String name;

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    private String description;

    @Size(max = 2000, message = "Requirements must not exceed 2000 characters")
    private String requirements;

    @Size(max = 2000, message = "References must not exceed 2000 characters")
    private String references;

    private TopicStatus status = TopicStatus.RECRUITING;
}
