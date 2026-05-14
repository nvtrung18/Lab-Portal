package com.web.labportalbackend.research.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class CommentResponse {
    private Long id;

    @JsonProperty("author_id")
    private Long authorId;

    private String content;

    @JsonProperty("created_at")
    private Instant createdAt;
}
