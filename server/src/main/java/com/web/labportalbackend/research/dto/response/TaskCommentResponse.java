package com.web.labportalbackend.research.dto.response;

import com.web.labportalbackend.research.enums.GroupRole;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class TaskCommentResponse {
    private Long id;
    private Long taskId;
    private Long authorId;
    private String authorName;
    private String authorEmail;
    private String authorRole;
    private GroupRole groupRole;
    private String content;
    private Instant createdAt;
}
